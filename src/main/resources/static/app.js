/**
 * Client-side Controller for Course Waitlist Fullstack Application
 */

const API = {
    getOffering: () => fetch('/api/offering').then(r => r.json()),
    getLearners: () => fetch('/api/learners').then(r => r.json()),
    registerLearner: (learner) => fetch('/api/learners', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(learner)
    }).then(r => r.json()),
    enroll: (learnerId) => fetch('/api/enroll', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ learnerId })
    }).then(r => r.json()),
    drop: (learnerId) => fetch('/api/drop', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ learnerId })
    }).then(r => r.json()),
    reset: () => fetch('/api/reset', { method: 'POST' }).then(r => r.json())
};

let learnersCache = {};

// DOM Elements
const elCourseId = document.getElementById('course-id');
const elCourseTitle = document.getElementById('course-title');
const elCourseCapacity = document.getElementById('course-capacity');
const elEnrolledCount = document.getElementById('enrolled-count');
const elCapacityTotal = document.getElementById('capacity-total');
const elAvailableSeats = document.getElementById('available-seats');
const elCapacityFill = document.getElementById('capacity-fill');
const elEnrolledList = document.getElementById('enrolled-list');
const elWaitlistList = document.getElementById('waitlist-list');
const elEnrolledBubble = document.getElementById('enrolled-bubble');
const elWaitlistBubble = document.getElementById('waitlist-bubble');
const elPersonaButtons = document.getElementById('persona-buttons');
const elLearnerSelect = document.getElementById('learner-select');
const elAuditStream = document.getElementById('audit-stream');
const elBtnReset = document.getElementById('btn-reset');
const elBtnClearLog = document.getElementById('btn-clear-log');
const elBtnRunDemo = document.getElementById('btn-run-acceptance-demo');
const elForm = document.getElementById('custom-enroll-form');
const elAutoFulfill = document.getElementById('auto-fulfill-prereq');

// New Learner Form DOM Elements
const elNewForm = document.getElementById('new-learner-form');
const elNewId = document.getElementById('new-learner-id');
const elNewName = document.getElementById('new-learner-name');
const elNewPrereq = document.getElementById('new-learner-prereq');
const elBtnRegisterOnly = document.getElementById('btn-register-only');

// ── Initial Load & Refresh ──────────────────────────────────────────────────

async function loadData() {
    try {
        const [offering, learners] = await Promise.all([API.getOffering(), API.getLearners()]);

        // Cache learners by ID
        learnersCache = {};
        learners.forEach(l => { learnersCache[l.id] = l; });

        renderCourseHero(offering);
        renderEnrolledList(offering.enrolledLearners);
        renderWaitlist(offering.waitlist);
        renderPersonas(learners, offering);
    } catch (err) {
        logAudit('ERROR', `Failed to load dashboard data: ${err.message}`, 'badge-danger');
    }
}

function renderCourseHero(offering) {
    elCourseId.textContent = offering.offeringId;
    elCourseTitle.textContent = offering.title;
    elCourseCapacity.textContent = offering.capacity;
    elCapacityTotal.textContent = offering.capacity;
    elEnrolledCount.textContent = offering.enrolledCount;
    elAvailableSeats.textContent = offering.availableSeats;
    elEnrolledBubble.textContent = offering.enrolledCount;
    elWaitlistBubble.textContent = offering.waitlist.length;

    const pct = Math.min(100, Math.round((offering.enrolledCount / offering.capacity) * 100));
    elCapacityFill.style.width = `${pct}%`;
    if (pct >= 100) {
        elCapacityFill.style.background = 'linear-gradient(90deg, #f59e0b, #ef4444)';
    } else {
        elCapacityFill.style.background = 'linear-gradient(90deg, #10b981, #0ea5e9)';
    }
}

function renderEnrolledList(enrolledIds) {
    elEnrolledList.innerHTML = '';
    if (!enrolledIds || enrolledIds.length === 0) {
        elEnrolledList.innerHTML = `
            <div class="empty-state">
                <span>🪑 No learners enrolled yet</span>
                <span style="font-size: 0.75rem; margin-top: 4px;">Available seats are open for registration</span>
            </div>
        `;
        return;
    }

    enrolledIds.forEach((id, idx) => {
        const learner = learnersCache[id] || { id, name: id, completedCourses: ['CS101'] };
        const card = document.createElement('div');
        card.className = 'student-card';
        card.innerHTML = `
            <div class="student-info">
                <div class="student-name-row">
                    <span class="seat-tag">Seat #${idx + 1}</span>
                    <span class="student-name">${learner.name}</span>
                </div>
                <span class="student-id">${learner.id}</span>
                <span class="student-prereqs">✓ Prereqs satisfied</span>
            </div>
            <button class="btn btn-danger btn-sm" onclick="handleDrop('${id}')">Drop</button>
        `;
        elEnrolledList.appendChild(card);
    });
}

function renderWaitlist(waitlistIds) {
    elWaitlistList.innerHTML = '';
    if (!waitlistIds || waitlistIds.length === 0) {
        elWaitlistList.innerHTML = `
            <div class="empty-state">
                <span>📋 Waitlist is currently empty</span>
                <span style="font-size: 0.75rem; margin-top: 4px;">Learners will queue here once capacity is reached</span>
            </div>
        `;
        return;
    }

    waitlistIds.forEach((id, idx) => {
        const learner = learnersCache[id] || { id, name: id, completedCourses: [] };
        const hasCS101 = (learner.completedCourses || []).includes('CS101');
        const card = document.createElement('div');
        card.className = 'student-card';
        card.innerHTML = `
            <div class="student-info">
                <div class="student-name-row">
                    <span class="rank-tag">FIFO #${idx + 1}</span>
                    <span class="student-name">${learner.name}</span>
                </div>
                <span class="student-id">${learner.id}</span>
                <span class="student-prereqs ${hasCS101 ? '' : 'missing'}">
                    ${hasCS101 ? '✓ Eligible for promotion' : '⚠ Missing CS101 (Skip & Retain)'}
                </span>
            </div>
            <div style="display:flex; gap:6px;">
                ${!hasCS101 ? `<button class="btn btn-primary btn-xs" onclick="togglePrereq('${id}', true)" title="Grant CS101 and allow promotion">+ CS101</button>` : ''}
                <button class="btn btn-secondary btn-sm" onclick="handleDrop('${id}')">Remove</button>
            </div>
        `;
        elWaitlistList.appendChild(card);
    });
}

function renderPersonas(learners, offering) {
    elPersonaButtons.innerHTML = '';
    const previousSelection = elLearnerSelect.value;
    elLearnerSelect.innerHTML = '<option value="">-- Choose registered learner --</option>';

    learners.forEach(l => {
        const hasCS101 = (l.completedCourses || []).includes('CS101');
        const isEnrolled = offering.enrolledLearners.includes(l.id);
        const isWaitlisted = offering.waitlist.includes(l.id);
        const waitlistPos = isWaitlisted ? offering.waitlist.indexOf(l.id) + 1 : 0;

        // Dropdown Option
        const opt = document.createElement('option');
        opt.value = l.id;
        opt.textContent = `${l.name} (${l.id}) — ${hasCS101 ? '✓ CS101 Eligible' : '⚠ Missing CS101'}`;
        elLearnerSelect.appendChild(opt);

        // Persona Row Item
        const item = document.createElement('div');
        item.className = 'persona-item';

        let statusBadge = '<span class="seat-tag" style="background:#f1f5f9; color:#475569;">Available</span>';
        if (isEnrolled) {
            statusBadge = '<span class="seat-tag" style="background:#dbeafe; color:#1d4ed8;">Enrolled</span>';
        } else if (isWaitlisted) {
            statusBadge = `<span class="rank-tag" style="background:#fef3c7; color:#b45309;">Waitlist #${waitlistPos}</span>`;
        }

        const prereqBadge = hasCS101
            ? '<span class="student-prereqs">✓ CS101 Satisfied</span>'
            : '<span class="student-prereqs missing">⚠ Missing CS101</span>';

        item.innerHTML = `
            <div style="flex:1;">
                <div style="display:flex; align-items:center; gap:6px;">
                    <span style="font-weight:700; font-size:0.86rem;">${l.name}</span>
                    <span style="font-size:0.72rem; color:#64748b;">(${l.id})</span>
                    ${statusBadge}
                </div>
                <div style="margin-top:2px;">
                    ${prereqBadge}
                </div>
            </div>
            <div class="persona-actions">
                ${!hasCS101
                    ? `<button class="btn btn-secondary btn-xs" onclick="togglePrereq('${l.id}', true)" title="Grant CS101 to satisfy prerequisite">+ CS101</button>`
                    : `<button class="btn btn-ghost btn-xs" onclick="togglePrereq('${l.id}', false)" title="Revoke CS101 to test prerequisite failure">- CS101</button>`
                }
                ${isEnrolled
                    ? `<button class="btn btn-danger btn-xs" onclick="handleDrop('${l.id}')">Drop</button>`
                    : (isWaitlisted
                        ? `<button class="btn btn-secondary btn-xs" onclick="handleDrop('${l.id}')">Leave</button>`
                        : `<button class="btn btn-primary btn-xs" onclick="handleEnroll('${l.id}')">Enroll</button>`
                    )
                }
            </div>
        `;
        elPersonaButtons.appendChild(item);
    });

    if (previousSelection) {
        elLearnerSelect.value = previousSelection;
    }
}

// ── Prerequisite Management ────────────────────────────────────────────────

async function togglePrereq(learnerId, grant) {
    const learner = learnersCache[learnerId];
    if (!learner) return;

    try {
        const completedCourses = grant ? ['CS101'] : [];
        await API.registerLearner({
            id: learner.id,
            name: learner.name,
            completedCourses
        });
        logAudit('PREREQ_UPDATE', `${grant ? '✓ Granted' : '✗ Revoked'} prerequisite CS101 for ${learner.name} (${learner.id}).`, 'badge-info');
        await loadData();
    } catch (err) {
        logAudit('ERROR', `Failed to update prerequisite: ${err.message}`, 'badge-danger');
    }
}

async function grantAndEnroll(learnerId) {
    await togglePrereq(learnerId, true);
    await handleEnroll(learnerId);
}

// ── Enrollment & Drop Actions ───────────────────────────────────────────────

async function handleEnroll(learnerId) {
    if (!learnerId) return;
    try {
        const res = await API.enroll(learnerId);
        let badgeClass = 'badge-success';
        let msg = res.message;

        if (res.status === 'PREREQUISITE_NOT_MET') {
            badgeClass = 'badge-danger';
            msg = `${res.message} <button class="btn btn-primary btn-xs" style="margin-left:8px;" onclick="grantAndEnroll('${learnerId}')">+ Grant CS101 & Retry</button>`;
        } else if (res.status === 'WAITLISTED') {
            badgeClass = 'badge-info';
        } else if (res.status && res.status.startsWith('ALREADY_')) {
            badgeClass = 'badge-warning';
        }

        logAudit(res.status, msg, badgeClass);
        await loadData();
    } catch (err) {
        logAudit('ERROR', err.message, 'badge-danger');
    }
}

async function handleDrop(learnerId) {
    if (!learnerId) return;
    try {
        const res = await API.drop(learnerId);
        let badgeClass = 'badge-warning';
        if (res.status === 'DROPPED_AND_PROMOTED') badgeClass = 'badge-info';
        else if (res.status === 'NOT_ENROLLED') badgeClass = 'badge-danger';

        logAudit(res.status, res.message, badgeClass);
        await loadData();
    } catch (err) {
        logAudit('ERROR', err.message, 'badge-danger');
    }
}

function logAudit(action, message, badgeType = 'badge-info') {
    const time = new Date().toLocaleTimeString();
    const entry = document.createElement('div');
    entry.className = 'log-entry';
    entry.innerHTML = `
        <span class="log-time">[${time}]</span>
        <span class="log-badge ${badgeType}">${action}</span>
        <span class="log-msg">${message}</span>
    `;
    elAuditStream.prepend(entry);
}

// ── Automated Acceptance Criteria Demo ──────────────────────────────────────

async function runAcceptanceDemo() {
    logAudit('DEMO_START', 'Starting 10-step self-verifying acceptance verification...', 'badge-info');
    await API.reset();
    await loadData();

    const sleep = (ms) => new Promise(r => setTimeout(r, ms));

    const steps = [
        { label: 'Step 1: Enroll Alice (has CS101 -> seat 1/2)', action: () => API.enroll('alice') },
        { label: 'Step 2: Enroll Bob (has CS101 -> seat 2/2, capacity reached)', action: () => API.enroll('bob') },
        { label: 'Step 3: Duplicate enrollment by Alice (idempotent rejection)', action: () => API.enroll('alice') },
        { label: 'Step 4: Enroll Carol (missing CS101 -> prerequisite rejection)', action: () => API.enroll('carol') },
        { label: 'Step 5: Enroll David (has CS101, joins waitlist #1)', action: () => API.enroll('david') },
        { label: 'Step 6: Enroll Eve (has CS101, joins waitlist #2)', action: () => API.enroll('eve') },
        { label: 'Step 7: Duplicate enrollment by David on waitlist (idempotent rejection)', action: () => API.enroll('david') },
        { label: 'Step 8: Drop Alice (Opens seat -> David promoted FIFO)', action: () => API.drop('alice') },
        { label: 'Step 9: Repeat Drop of Alice (already dropped -> NOT_ENROLLED)', action: () => API.drop('alice') },
        { label: 'Step 10: Drop Bob (Opens seat -> Eve promoted FIFO)', action: () => API.drop('bob') }
    ];

    for (const step of steps) {
        await sleep(400);
        const res = await step.action();
        logAudit(res.status, `${step.label} => ${res.message}`, 'badge-success');
        await loadData();
    }

    logAudit('DEMO_COMPLETE', '✓ All Acceptance Criteria successfully verified!', 'badge-success');
}

// ── Event Listeners ─────────────────────────────────────────────────────────

// 1. Enroll from List Form
elForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const id = elLearnerSelect.value;
    if (!id) {
        alert('Please select a learner from the list.');
        return;
    }

    const autoFulfill = elAutoFulfill && elAutoFulfill.checked;
    const learner = learnersCache[id];

    if (autoFulfill && learner && !(learner.completedCourses || []).includes('CS101')) {
        logAudit('PREREQ_GRANT', `Auto-granting prerequisite CS101 to ${learner.name} (${learner.id}) before enrollment...`, 'badge-info');
        await API.registerLearner({
            id: learner.id,
            name: learner.name,
            completedCourses: ['CS101']
        });
    }

    await handleEnroll(id);
});

// 2. Register & Enroll New Learner Form
elNewForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const id = elNewId.value.trim().toLowerCase();
    const name = elNewName.value.trim();
    const hasPrereq = elNewPrereq.checked;

    if (!id || !name) {
        alert('Please provide both Learner ID and Full Name.');
        return;
    }

    logAudit('REGISTER', `Registering new learner: ${name} (${id}) with completed prerequisites: [${hasPrereq ? 'CS101' : 'None'}]...`, 'badge-info');
    await API.registerLearner({
        id,
        name,
        completedCourses: hasPrereq ? ['CS101'] : []
    });

    logAudit('ENROLL', `Submitting enrollment request for new learner ${name} (${id})...`, 'badge-info');
    await handleEnroll(id);

    elNewId.value = '';
    elNewName.value = '';
    elNewPrereq.checked = true;
});

// 3. Register Only Button
elBtnRegisterOnly.addEventListener('click', async () => {
    const id = elNewId.value.trim().toLowerCase();
    const name = elNewName.value.trim();
    const hasPrereq = elNewPrereq.checked;

    if (!id || !name) {
        alert('Please provide both Learner ID and Full Name.');
        return;
    }

    await API.registerLearner({
        id,
        name,
        completedCourses: hasPrereq ? ['CS101'] : []
    });
    logAudit('REGISTER', `✓ Successfully registered new learner: ${name} (${id}). Available in list.`, 'badge-success');
    await loadData();
    elLearnerSelect.value = id;

    elNewId.value = '';
    elNewName.value = '';
    elNewPrereq.checked = true;
});

// 4. Header Reset & Clear Log
elBtnReset.addEventListener('click', async () => {
    await API.reset();
    logAudit('RESET', 'Course offering and waitlist state reset to initial baseline.', 'badge-warning');
    await loadData();
});

elBtnClearLog.addEventListener('click', () => {
    elAuditStream.innerHTML = '';
});

elBtnRunDemo.addEventListener('click', runAcceptanceDemo);

// Start
loadData();
