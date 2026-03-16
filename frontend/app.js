const API_BASE_URL = 'http://localhost:8080';
let editor;
let sessionId = null;
let currentLanguage = 'python';
let autosaveTimeout = null;

const defaultCode = {
    python: 'print("Hello from Edtronaut!")\n\n# Try some logic:\nfor i in range(5):\n    print(f"Iteration {i}")',
    nodejs: 'console.log("Hello from Edtronaut!");\n\nconst fruits = ["Apple", "Banana", "Cherry"];\nfruits.forEach(f => console.log(`Fruit: ${f}`));',
    java: 'public class Main {\n    public static void main(String[] args) {\n        System.out.println("Hello from Edtronaut!");\n        \n        int sum = 0;\n        for (int i = 1; i <= 10; i++) {\n            sum += i;\n        }\n        System.out.println("Sum of 1-10 is: " + sum);\n    }\n}'
};

const modeMap = {
    python: 'ace/mode/python',
    nodejs: 'ace/mode/javascript',
    java: 'ace/mode/java'
};

// Elements
const runBtn = document.getElementById('run-btn');
const newSessionBtn = document.getElementById('new-session-btn');
const langSelect = document.getElementById('language-select');
const consoleOutput = document.getElementById('console-output');
const saveStatus = document.getElementById('save-status');
const sessionDisplay = document.getElementById('session-display');
const fileNameDisplay = document.getElementById('file-name-display');
const executionBadge = document.getElementById('execution-status-badge');
const executionTime = document.getElementById('execution-time');

// Initialize Ace Editor
function initEditor() {
    editor = ace.edit("editor-container");
    editor.setTheme("ace/theme/monokai");
    editor.session.setMode(modeMap[currentLanguage]);
    editor.setValue(defaultCode[currentLanguage], -1);

    editor.setOptions({
        fontSize: "14px",
        fontFamily: "JetBrains Mono",
        showPrintMargin: false,
        enableBasicAutocompletion: true,
        enableLiveAutocompletion: true
    });

    editor.getSession().on('change', () => {
        triggerAutosave();
    });

    bootstrap();
}

async function bootstrap() {
    const savedSession = localStorage.getItem('edtronaut_session_id');
    if (savedSession) {
        sessionId = savedSession;
        updateUIWithSession();
    } else {
        await createNewSession();
    }
}

async function createNewSession() {
    try {
        setSaveStatus('Connecting...');
        const response = await fetch(`${API_BASE_URL}/code-sessions`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ language: currentLanguage })
        });
        const data = await response.json();
        sessionId = data.sessionId || data.session_id; // Handle both camelCase and snake_case
        localStorage.setItem('edtronaut_session_id', sessionId);
        updateUIWithSession();
        setSaveStatus('Ready');
    } catch (err) {
        console.error('Failed to create session:', err);
        setSaveStatus('Offline');
        appendLog('System', '⚠️ Could not connect to backend server at ' + API_BASE_URL, 'stderr');
    }
}

function updateUIWithSession() {
    if (sessionId) {
        sessionDisplay.innerText = `Session: ${sessionId.substring(0, 8)}...`;
    }
}

function setSaveStatus(status) {
    saveStatus.innerText = status;
}

function triggerAutosave() {
    setSaveStatus('Editing...');
    if (autosaveTimeout) clearTimeout(autosaveTimeout);
    autosaveTimeout = setTimeout(async () => {
        if (!sessionId) return;
        try {
            setSaveStatus('Saving...');
            await fetch(`${API_BASE_URL}/code-sessions/${sessionId}`, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    language: currentLanguage,
                    //source_code: editor.getValue(),
                    //sourceCode: editor.getValue() // Compatibility for both
                })
            });
            setSaveStatus('Saved');
        } catch (err) {
            console.error('Autosave failed:', err);
            setSaveStatus('Save Error');
        }
    }, 1000);
}

langSelect.addEventListener('change', (e) => {
    const newLang = e.target.value;
    currentLanguage = newLang;

    editor.session.setMode(modeMap[newLang]);
    editor.setValue(defaultCode[newLang], -1);

    const extensions = { python: 'main.py', nodejs: 'index.js', java: 'Main.java' };
    fileNameDisplay.innerText = extensions[newLang];

    triggerAutosave();
});

newSessionBtn.addEventListener('click', async () => {
    localStorage.removeItem('edtronaut_session_id');
    sessionId = null;
    await createNewSession();
    appendLog('System', 'New session created.', 'system-msg');
});

runBtn.addEventListener('click', async () => {
    if (!sessionId) {
        appendLog('System', 'No active session. Please create one.', 'stderr');
        return;
    }

    try {
        runBtn.disabled = true;
        setExecutionStatus('QUEUED');
        consoleOutput.innerHTML = '';
        appendLog('System', 'Requesting execution...', 'system-msg');

        // Force save before run
        await fetch(`${API_BASE_URL}/code-sessions/${sessionId}`, {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                language: currentLanguage,
                sourceCode: editor.getValue(),
                source_code: editor.getValue()
            })
        });

        const response = await fetch(`${API_BASE_URL}/code-sessions/${sessionId}/run`, {
            method: 'POST'
        });
        const data = await response.json();

        pollResult(data.executionId || data.execution_id);
    } catch (err) {
        console.error('Run failed:', err);
        appendLog('System', 'Failed to trigger execution.', 'stderr');
        runBtn.disabled = false;
        setExecutionStatus('IDLE');
    }
});

async function pollResult(executionId) {
    let attempts = 0;
    const maxAttempts = 30;

    const interval = setInterval(async () => {
        attempts++;
        if (attempts > maxAttempts) {
            clearInterval(interval);
            appendLog('System', 'Polling timed out.', 'stderr');
            runBtn.disabled = false;
            setExecutionStatus('TIMEOUT');
            return;
        }

        try {
            const response = await fetch(`${API_BASE_URL}/executions/${executionId}`);
            if (response.status === 200) {
                const data = await response.json();
                setExecutionStatus(data.status);

                if (data.status === 'COMPLETED' || data.status === 'FAILED' || data.status === 'TIMEOUT') {
                    clearInterval(interval);
                    runBtn.disabled = false;
                    displayResult(data);
                }
            }
        } catch (err) {
            console.error('Polling error:', err);
        }
    }, 1000);
}

function displayResult(result) {
    consoleOutput.innerHTML = '';
    if (result.stdout) appendLog('Output', result.stdout, 'stdout');
    if (result.stderr) appendLog('Error', result.stderr, 'stderr');
    if (!result.stdout && !result.stderr) appendLog('System', 'Finished (no output).', 'system-msg');

    const time = result.executionTimeMs || result.execution_time_ms || 0;
    executionTime.innerText = `Finished in ${time}ms`;
}

function appendLog(label, text, className) {
    const div = document.createElement('div');
    div.className = className;
    div.innerText = label === 'System' ? `[${label}] ${text}` : text;
    consoleOutput.appendChild(div);
    consoleOutput.scrollTop = consoleOutput.scrollHeight;
}

function setExecutionStatus(status) {
    executionBadge.innerText = status;
    executionBadge.className = 'badge ' + status.toLowerCase();
}

// Start
window.onload = initEditor;
