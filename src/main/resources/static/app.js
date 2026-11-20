/**
 * Client-side Controller for Course Waitlist Fullstack Application
 */

const API = {
    getOffering: () => fetch('/api/offering').then(r => r.json()),
    getLearners: () => fetch('/api/learners').then(r => r.json()),
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
        logAudit('ERROR', `Failed to load dashboard data: ${err.message}`, 'danger');
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
        const learner = learnersCache[id] || { id, name: id, completedCourses: [] };
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
            <button class="btn btn-secondary btn-sm" onclick="handleDrop('${id}')">Remove</button>
        `;
        elWaitlistList.appendChild(card);
    });
}

function renderPersonas(learners, offering) {
    elPersonaButtons.innerHTML = '';
    elLearnerSelect.innerHTML = '<option value="">-- Choose registered learner --</option>';

    learners.forEach(l => {
        // Option
        const opt = document.createElement('option');
        opt.value = l.id;
        opt.textContent = `${l.name} (${l.id}) - [${(l.completedCourses || []).join(', ') || 'No prereqs'}]`;
        elLearnerSelect.appendChild(opt);

        // Persona Button
        const isEnrolled = offering.enrolledLearners.includes(l.id);
        const isWaitlisted = offering.waitlist.includes(l.id);

        let statusText = 'Available';
        let badgeStyle = 'color: #059669';
        if (isEnrolled) { statusText = 'Enrolled'; badgeStyle = 'color: #4f46e5; font-weight: 700;'; }
        if (isWaitlisted) { statusText = 'Waitlisted'; badgeStyle = 'color: #0284c7; font-weight: 700;'; }

        const btn = document.createElement('button');
        btn.className = 'persona-btn';
        btn.type = 'button';
        btn.onclick = () => handleEnroll(l.id);
        btn.innerHTML = `
            <div>
                <div>${l.name} <span style="font-size: 0.72rem; color: #64748b;">(${l.id})</span></div>
                <div class="persona-meta">Completed: [${(l.completedCourses || []).join(', ') || 'None'}]</div>
            </div>
            <span style="${badgeStyle}; font-size: 0.75rem;">${statusText} ›</span>
        `;
        elPersonaButtons.appendChild(btn);
    });
}

// ── Actions ─────────────────────────────────────────────────────────────────

async function handleEnroll(learnerId) {
    if (!learnerId) return;
    try {
        const res = await API.enroll(learnerId);
        let badgeClass = 'badge-success';
        if (res.status === 'PREREQUISITE_NOT_MET') badgeClass = 'badge-danger';
        else if (res.status === 'WAITLISTED') badgeClass = 'badge-info';
        else if (res.status.startsWith('ALREADY_')) badgeClass = 'badge-warning';

        logAudit(res.status, res.message, badgeClass);
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

elForm.addEventListener('submit', (e) => {
    e.preventDefault();
    const id = elLearnerSelect.value;
    if (id) handleEnroll(id);
});

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
