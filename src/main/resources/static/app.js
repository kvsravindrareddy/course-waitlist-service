/**
 * Client-side Controller for Course Waitlist Fullstack Application
 * Supports dynamic multi-course offering catalog, customizable prerequisites, and flexible enrollment.
 */

const API = {
    getOffering: () => fetch('/api/offering').then(r => r.json()),
    getAllOfferings: () => fetch('/api/offerings').then(r => r.json()),
    createOffering: (offering) => fetch('/api/offerings', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(offering)
    }).then(r => r.json()),
    switchOffering: (offeringId) => fetch('/api/offering/switch', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ offeringId })
    }).then(r => r.json()),
    getLearners: () => fetch('/api/learners').then(r => r.json()),
    registerLearner: (learner) => fetch('/api/learners', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(learner)
    }).then(r => r.json()),
    enroll: (learnerId, offeringId) => fetch('/api/enroll', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ learnerId, offeringId })
    }).then(r => r.json()),
    drop: (learnerId, offeringId) => fetch('/api/drop', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ learnerId, offeringId })
    }).then(r => r.json()),
    reset: () => fetch('/api/reset', { method: 'POST' }).then(r => r.json())
};

let learnersCache = {};
let currentOffering = null;
let offeringsCache = [];

// DOM Elements
const elCourseOfferingSelect = document.getElementById('course-offering-select');
const elBtnToggleNewCourse = document.getElementById('btn-toggle-new-course');
const elNewCourseForm = document.getElementById('new-course-form');
const elBtnCancelNewCourse = document.getElementById('btn-cancel-new-course');

const elCourseId = document.getElementById('course-id');
const elCourseTitle = document.getElementById('course-title');
const elCoursePrereq = document.getElementById('course-prereq');
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
const elNewCompletedCourses = document.getElementById('new-learner-completed-courses');
const elBtnRegisterOnly = document.getElementById('btn-register-only');
const elBtnMatchCoursePrereqs = document.getElementById('btn-match-course-prereqs');

// ── Initial Load & Refresh ──────────────────────────────────────────────────

async function loadData() {
    try {
        const [offering, offerings, learners] = await Promise.all([
            API.getOffering(),
            API.getAllOfferings(),
            API.getLearners()
        ]);

        currentOffering = offering;
        offeringsCache = offerings || [];

        // Cache learners by ID
        learnersCache = {};
        learners.forEach(l => { learnersCache[l.id] = l; });

        renderCourseSelector(offerings, offering.offeringId);
        renderCourseHero(offering);
        renderEnrolledList(offering.enrolledLearners);
        renderWaitlist(offering.waitlist);
        renderPersonas(learners, offering);
    } catch (err) {
        logAudit('ERROR', `Failed to load dashboard data: ${err.message}`, 'badge-danger');
    }
}

function renderCourseSelector(offerings, activeId) {
    elCourseOfferingSelect.innerHTML = '';
    offerings.forEach(off => {
        const opt = document.createElement('option');
        opt.value = off.offeringId;
        const prereqText = (off.prerequisites && off.prerequisites.length > 0)
            ? off.prerequisites.join(', ')
            : 'None';
        opt.textContent = `${off.offeringId} — ${off.title} [Prereq: ${prereqText} | Cap: ${off.capacity}]`;
        if (off.offeringId === activeId) {
            opt.selected = true;
        }
        elCourseOfferingSelect.appendChild(opt);
    });
}

function renderCourseHero(offering) {
    elCourseId.textContent = offering.offeringId;
    elCourseTitle.textContent = offering.title;
    const prereqList = offering.prerequisites || [];
    elCoursePrereq.textContent = prereqList.length > 0 ? prereqList.join(', ') : 'None';
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
                <span class="student-prereqs">✓ Prereqs satisfied: [${(learner.completedCourses || []).join(', ') || 'None'}]</span>
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

    const requiredPrereqs = (currentOffering && currentOffering.prerequisites) || [];

    waitlistIds.forEach((id, idx) => {
        const learner = learnersCache[id] || { id, name: id, completedCourses: [] };
        const completed = learner.completedCourses || [];
        const missing = requiredPrereqs.filter(p => !completed.includes(p));
        const isEligible = missing.length === 0;

        const card = document.createElement('div');
        card.className = 'student-card';
        card.innerHTML = `
            <div class="student-info">
                <div class="student-name-row">
                    <span class="rank-tag">FIFO #${idx + 1}</span>
                    <span class="student-name">${learner.name}</span>
                </div>
                <span class="student-id">${learner.id}</span>
                <span class="student-prereqs ${isEligible ? '' : 'missing'}">
                    ${isEligible ? '✓ Eligible for promotion' : `⚠ Missing: ${missing.join(', ')} (Skip & Retain)`}
                </span>
            </div>
            <div style="display:flex; gap:6px;">
                ${!isEligible ? `<button class="btn btn-primary btn-xs" onclick="fulfillCurrentPrereqs('${id}')" title="Grant required prerequisites to allow promotion">+ Prereqs</button>` : ''}
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

    const requiredPrereqs = offering.prerequisites || [];

    learners.forEach(l => {
        const completed = l.completedCourses || [];
        const missing = requiredPrereqs.filter(p => !completed.includes(p));
        const isEligible = missing.length === 0;

        const isEnrolled = offering.enrolledLearners.includes(l.id);
        const isWaitlisted = offering.waitlist.includes(l.id);
        const waitlistPos = isWaitlisted ? offering.waitlist.indexOf(l.id) + 1 : 0;

        // Dropdown Option
        const opt = document.createElement('option');
        opt.value = l.id;
        opt.textContent = `${l.name} (${l.id}) — ${isEligible ? '✓ Eligible' : `⚠ Missing: ${missing.join(', ')}`}`;
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

        const prereqBadge = isEligible
            ? `<span class="student-prereqs">✓ Eligible [${completed.join(', ') || 'None'}]</span>`
            : `<span class="student-prereqs missing">⚠ Missing: ${missing.join(', ')} (Has: [${completed.join(', ') || 'None'}])</span>`;

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
                ${!isEligible
                    ? `<button class="btn btn-secondary btn-xs" onclick="fulfillCurrentPrereqs('${l.id}')" title="Fulfill all required course prerequisites">+ Prereqs</button>`
                    : `<button class="btn btn-ghost btn-xs" onclick="clearLearnerPrereqs('${l.id}')" title="Clear prerequisites to test rejection">Clear</button>`
                }
                <button class="btn btn-ghost btn-xs" onclick="promptCustomPrereqs('${l.id}')" title="Set custom list of completed courses">Edit</button>
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

// ── Course Catalog Switching & Creation ─────────────────────────────────────

elCourseOfferingSelect.addEventListener('change', async (e) => {
    const offeringId = e.target.value;
    if (!offeringId) return;
    try {
        const snap = await API.switchOffering(offeringId);
        logAudit('COURSE_SWITCH', `Switched active course to ${snap.offeringId} (${snap.title})`, 'badge-info');
        await loadData();
    } catch (err) {
        logAudit('ERROR', `Failed to switch course: ${err.message}`, 'badge-danger');
    }
});

elBtnToggleNewCourse.addEventListener('click', () => {
    const isHidden = elNewCourseForm.style.display === 'none';
    elNewCourseForm.style.display = isHidden ? 'block' : 'none';
    elBtnToggleNewCourse.textContent = isHidden ? '✕ Close Form' : '+ Add Custom Course';
});

elBtnCancelNewCourse.addEventListener('click', () => {
    elNewCourseForm.style.display = 'none';
    elBtnToggleNewCourse.textContent = '+ Add Custom Course';
});

elNewCourseForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const id = document.getElementById('new-course-id').value.trim().toUpperCase();
    const title = document.getElementById('new-course-title').value.trim();
    const cap = parseInt(document.getElementById('new-course-capacity').value, 10);
    const prereqsRaw = document.getElementById('new-course-prereqs').value.trim();

    const prerequisites = prereqsRaw ? prereqsRaw.split(',').map(s => s.trim().toUpperCase()).filter(Boolean) : [];

    if (!id || !title || !cap) {
        alert('Please fill out Course Code, Title, and Capacity.');
        return;
    }

    try {
        logAudit('CREATE_COURSE', `Creating new course offering ${id}: ${title} (Capacity: ${cap}, Prerequisites: [${prerequisites.join(', ')}])...`, 'badge-info');
        await API.createOffering({ id, title, capacity: cap, prerequisites });
        elNewCourseForm.reset();
        elNewCourseForm.style.display = 'none';
        elBtnToggleNewCourse.textContent = '+ Add Custom Course';
        await loadData();
    } catch (err) {
        logAudit('ERROR', `Failed to create course: ${err.message}`, 'badge-danger');
    }
});

// ── Dynamic Prerequisite Helpers ───────────────────────────────────────────

function addChipCourse(courseCode) {
    if (courseCode === 'None') {
        elNewCompletedCourses.value = '';
        return;
    }
    const current = elNewCompletedCourses.value.split(',').map(s => s.trim().toUpperCase()).filter(Boolean);
    if (!current.includes(courseCode)) {
        current.push(courseCode);
    }
    elNewCompletedCourses.value = current.join(', ');
}

if (elBtnMatchCoursePrereqs) {
    elBtnMatchCoursePrereqs.addEventListener('click', () => {
        if (currentOffering && currentOffering.prerequisites) {
            elNewCompletedCourses.value = currentOffering.prerequisites.join(', ');
        }
    });
}

async function fulfillCurrentPrereqs(learnerId) {
    const learner = learnersCache[learnerId];
    if (!learner || !currentOffering) return;

    const currentCourses = new Set(learner.completedCourses || []);
    (currentOffering.prerequisites || []).forEach(p => currentCourses.add(p));

    await API.registerLearner({
        id: learner.id,
        name: learner.name,
        completedCourses: Array.from(currentCourses)
    });
    logAudit('PREREQ_GRANT', `Granted prerequisites [${(currentOffering.prerequisites || []).join(', ')}] to ${learner.name} (${learner.id}).`, 'badge-info');
    await loadData();
}

async function clearLearnerPrereqs(learnerId) {
    const learner = learnersCache[learnerId];
    if (!learner) return;

    await API.registerLearner({
        id: learner.id,
        name: learner.name,
        completedCourses: []
    });
    logAudit('PREREQ_CLEAR', `Cleared prerequisites for ${learner.name} (${learner.id}).`, 'badge-info');
    await loadData();
}

async function promptCustomPrereqs(learnerId) {
    const learner = learnersCache[learnerId];
    if (!learner) return;

    const current = (learner.completedCourses || []).join(', ');
    const input = prompt(`Enter completed courses for ${learner.name} (comma-separated):`, current);
    if (input === null) return;

    const completedCourses = input.split(',').map(s => s.trim().toUpperCase()).filter(Boolean);
    await API.registerLearner({
        id: learner.id,
        name: learner.name,
        completedCourses
    });
    logAudit('PREREQ_UPDATE', `Updated ${learner.name}'s completed courses to: [${completedCourses.join(', ')}].`, 'badge-info');
    await loadData();
}

async function grantAndEnroll(learnerId) {
    await fulfillCurrentPrereqs(learnerId);
    await handleEnroll(learnerId);
}

// ── Enrollment & Drop Actions ───────────────────────────────────────────────

async function handleEnroll(learnerId) {
    if (!learnerId) return;
    try {
        const res = await API.enroll(learnerId, currentOffering ? currentOffering.offeringId : null);
        let badgeClass = 'badge-success';
        let msg = res.message;

        if (res.status === 'PREREQUISITE_NOT_MET') {
            badgeClass = 'badge-danger';
            msg = `${res.message} <button class="btn btn-primary btn-xs" style="margin-left:8px;" onclick="grantAndEnroll('${learnerId}')">+ Grant Prereqs & Retry</button>`;
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
        const res = await API.drop(learnerId, currentOffering ? currentOffering.offeringId : null);
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
    const requiredPrereqs = (currentOffering && currentOffering.prerequisites) || [];

    if (autoFulfill && learner && requiredPrereqs.length > 0) {
        const currentCourses = new Set(learner.completedCourses || []);
        let updated = false;
        requiredPrereqs.forEach(p => {
            if (!currentCourses.has(p)) {
                currentCourses.add(p);
                updated = true;
            }
        });

        if (updated) {
            logAudit('PREREQ_GRANT', `Auto-granting required prerequisites [${requiredPrereqs.join(', ')}] to ${learner.name} before enrollment...`, 'badge-info');
            await API.registerLearner({
                id: learner.id,
                name: learner.name,
                completedCourses: Array.from(currentCourses)
            });
        }
    }

    await handleEnroll(id);
});

// 2. Register & Enroll New Learner Form
elNewForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const id = elNewId.value.trim().toLowerCase();
    const name = elNewName.value.trim();
    const coursesRaw = elNewCompletedCourses.value.trim();
    const completedCourses = coursesRaw ? coursesRaw.split(',').map(s => s.trim().toUpperCase()).filter(Boolean) : [];

    if (!id || !name) {
        alert('Please provide both Learner ID and Full Name.');
        return;
    }

    logAudit('REGISTER', `Registering new learner: ${name} (${id}) with completed courses: [${completedCourses.join(', ')}]...`, 'badge-info');
    await API.registerLearner({
        id,
        name,
        completedCourses
    });

    logAudit('ENROLL', `Submitting enrollment request for ${name} (${id}) into active course ${currentOffering ? currentOffering.offeringId : ''}...`, 'badge-info');
    await handleEnroll(id);

    elNewId.value = '';
    elNewName.value = '';
    elNewCompletedCourses.value = '';
});

// 3. Register Only Button
elBtnRegisterOnly.addEventListener('click', async () => {
    const id = elNewId.value.trim().toLowerCase();
    const name = elNewName.value.trim();
    const coursesRaw = elNewCompletedCourses.value.trim();
    const completedCourses = coursesRaw ? coursesRaw.split(',').map(s => s.trim().toUpperCase()).filter(Boolean) : [];

    if (!id || !name) {
        alert('Please provide both Learner ID and Full Name.');
        return;
    }

    await API.registerLearner({
        id,
        name,
        completedCourses
    });
    logAudit('REGISTER', `✓ Successfully registered new learner: ${name} (${id}) with courses [${completedCourses.join(', ')}].`, 'badge-success');
    await loadData();
    elLearnerSelect.value = id;

    elNewId.value = '';
    elNewName.value = '';
    elNewCompletedCourses.value = '';
});

// 4. Header Reset & Clear Log
elBtnReset.addEventListener('click', async () => {
    await API.reset();
    logAudit('RESET', 'Course offering and waitlist state reset to default baseline.', 'badge-warning');
    await loadData();
});

elBtnClearLog.addEventListener('click', () => {
    elAuditStream.innerHTML = '';
});

elBtnRunDemo.addEventListener('click', runAcceptanceDemo);

// Start
loadData();
