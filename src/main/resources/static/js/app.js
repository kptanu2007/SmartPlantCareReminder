const fallbackImage = '/images/plant-leaves.jpg';
let csrf = '';
let currentUser = null;
let dashboard = null;
let plants = [];
let reminders = [];
let history = [];
let toastTimer;

const byId = id => document.getElementById(id);
const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, character => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
}[character]));

async function refreshCsrf() {
    const response = await fetch('/api/auth/csrf', { credentials: 'same-origin' });
    if (!response.ok) throw new Error('Could not establish a secure session.');
    csrf = (await response.json()).token;
}

async function api(path, options = {}) {
    const headers = new Headers(options.headers || {});
    if (options.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
    if (options.method && !['GET', 'HEAD', 'OPTIONS'].includes(options.method.toUpperCase())) {
        if (!csrf) await refreshCsrf();
        headers.set('X-CSRF-TOKEN', csrf);
    }
    const response = await fetch(path, { ...options, headers, credentials: 'same-origin' });
    if (response.status === 401) {
        window.location.replace('/login.html');
        throw new Error('Your session has expired. Please log in again.');
    }
    if (response.status === 204) return null;
    const result = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(result.message || `The request failed (${response.status}).`);
    return result;
}

function toast(message) {
    const node = byId('toast');
    node.textContent = message;
    node.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => node.classList.remove('show'), 2800);
}

function dateOnly(value) {
    if (!value) return '';
    return new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
        .format(new Date(`${value}T12:00:00`));
}

function dateTime(value) {
    return new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
        .format(new Date(value));
}

function careDue(plant) {
    return plant.nextWateringDate <= new Date().toISOString().slice(0, 10);
}

function setImageFallbacks(root = document) {
    root.querySelectorAll('img[data-plant-image]').forEach(image => {
        image.addEventListener('error', () => {
            image.onerror = null;
            image.src = fallbackImage;
        }, { once: true });
    });
}

function plantCard(plant) {
    const due = careDue(plant);
    return `<article class="plant-card">
        <div class="plant-image-wrap">
            <img data-plant-image src="${escapeHtml(plant.imageUrl || fallbackImage)}" alt="${escapeHtml(plant.species)} foliage" loading="lazy">
            <span class="plant-status${due ? ' due' : ''}">${due ? 'Care due' : 'On schedule'}</span>
        </div>
        <div class="plant-card-body">
            <div class="plant-card-heading"><div><h3>${escapeHtml(plant.name)}</h3><p>${escapeHtml(plant.species)} · ${escapeHtml(plant.location)}</p></div></div>
            <div class="plant-meta"><span>Water every ${plant.wateringIntervalDays} days</span><span>Next: ${dateOnly(plant.nextWateringDate)}</span></div>
            <div class="plant-card-actions"><button type="button" data-water="${escapeHtml(plant.id)}">Watered today</button><button type="button" data-details="${escapeHtml(plant.id)}">Details</button><button type="button" data-edit="${escapeHtml(plant.id)}">Edit</button><button class="danger-link" type="button" data-delete="${escapeHtml(plant.id)}">Remove</button></div>
        </div>
    </article>`;
}

function emptyState(title, text, action = '') {
    return `<div class="empty-state"><strong>${escapeHtml(title)}</strong>${escapeHtml(text)}${action ? `<br><button class="button button-primary" data-add-plant type="button">${action}</button>` : ''}</div>`;
}

function renderPlants() {
    byId('plant-nav-count').textContent = plants.length;
    byId('stat-total').textContent = dashboard.totalPlants;
    byId('stat-due').textContent = dashboard.plantsDue;
    byId('stat-schedule').textContent = dashboard.plantsOnSchedule;
    const overview = plants.slice(0, 3);
    byId('overview-plants').innerHTML = plants.length
        ? overview.map(plantCard).join('')
        : emptyState('A fresh start.', 'Your collection is waiting for its first plant.', 'Add your first plant');
    applyPlantFilters();
    setImageFallbacks();
}

function applyPlantFilters() {
    const search = (byId('plant-search')?.value || '').trim().toLowerCase();
    const filter = byId('plant-filter')?.value || 'all';
    const visible = plants.filter(plant => {
        const matchesSearch = `${plant.name} ${plant.species} ${plant.location}`.toLowerCase().includes(search);
        const matchesFilter = filter === 'all' || (filter === 'due' ? careDue(plant) : !careDue(plant));
        return matchesSearch && matchesFilter;
    });
    byId('all-plants').innerHTML = visible.length
        ? visible.map(plantCard).join('')
        : plants.length ? emptyState('No plants found.', 'Try a different search or filter.')
            : emptyState('Your garden starts here.', 'Add a plant to keep its care routine close at hand.', 'Add a plant');
}

function reminderLabel(date) {
    const today = new Date().toISOString().slice(0, 10);
    if (date < today) return 'Overdue';
    if (date === today) return 'Due today';
    return 'Upcoming';
}

function renderReminders() {
    const open = reminders.filter(reminder => !reminder.completed);
    const plantById = new Map(plants.map(plant => [plant.id, plant]));
    const overview = open.slice(0, 4);
    byId('overview-reminders').innerHTML = overview.length
        ? overview.map(reminder => {
            const plant = plantById.get(reminder.plantId);
            if (!plant) return '';
            return `<div class="simple-reminder"><span><strong>${escapeHtml(plant.name)}</strong><small>Watering · ${escapeHtml(plant.location)}</small></span><time>${reminderLabel(reminder.dueDate)}<br>${dateOnly(reminder.dueDate)}</time></div>`;
        }).join('') || emptyState('All caught up.', 'Your garden is looking good.')
        : emptyState('All caught up.', 'New care reminders will appear here when they are due.');
    byId('reminders-list').innerHTML = open.length
        ? open.map(reminder => {
            const plant = plantById.get(reminder.plantId);
            if (!plant) return '';
            const dueDate = new Date(`${reminder.dueDate}T12:00:00`);
            const label = reminderLabel(reminder.dueDate);
            return `<article class="reminder-item"><div class="reminder-date"><strong>${dueDate.getDate()}</strong><span>${new Intl.DateTimeFormat(undefined, { month: 'short' }).format(dueDate)}</span></div><div class="reminder-description"><strong>${escapeHtml(plant.name)}</strong><p>${escapeHtml(plant.species)} · Watering reminder · ${escapeHtml(plant.location)}</p><span class="text-status${label === 'Overdue' ? ' overdue' : ''}">${label}</span></div><button class="button button-quiet" type="button" data-complete="${escapeHtml(reminder.id)}">Mark watered</button></article>`;
        }).join('') || emptyState('No reminders yet.', 'Add a plant to start a care schedule.', 'Add a plant')
        : open.length ? emptyState('No reminders yet.', 'Add a plant to start a care schedule.', 'Add a plant')
            : emptyState('No reminders yet.', 'Add a plant to start a care schedule.', 'Add a plant');
}

function renderHistory() {
    const plantById = new Map(plants.map(plant => [plant.id, plant]));
    byId('history-list').innerHTML = history.length
        ? history.map(entry => `<div class="history-row"><span class="history-icon" aria-hidden="true">↗</span><div class="history-copy"><strong>${escapeHtml(entry.action)} · ${escapeHtml(plantById.get(entry.plantId)?.name || 'Plant')}</strong><p>${escapeHtml(entry.notes || 'Recorded in your garden care history.')}</p></div><time>${dateTime(entry.occurredAt)}</time></div>`).join('')
        : emptyState('Your care story begins here.', 'Water a plant to see your first care activity.');
}

async function loadGarden() {
    [currentUser, dashboard, plants, reminders, history] = await Promise.all([
        api('/api/auth/me'), api('/api/dashboard'), api('/api/plants'), api('/api/reminders'), api('/api/history')
    ]);
    byId('welcome-name').textContent = currentUser.fullName.split(/\s+/)[0];
    byId('sidebar-name').textContent = currentUser.fullName;
    byId('sidebar-email').textContent = currentUser.email;
    byId('user-initial').textContent = currentUser.fullName.trim().charAt(0).toUpperCase();
    byId('today-label').textContent = new Intl.DateTimeFormat(undefined, { weekday: 'long', month: 'long', day: 'numeric' }).format(new Date()).toUpperCase();
    renderPlants();
    renderReminders();
    renderHistory();
}

function switchView(name) {
    document.querySelectorAll('.view-panel').forEach(panel => panel.classList.toggle('active', panel.id === `view-${name}`));
    document.querySelectorAll('.nav-item').forEach(button => button.classList.toggle('active', button.dataset.view === name));
    const headings = { overview: 'A good day to grow.', plants: 'Your plant collection.', reminders: 'Care, at the right moment.', history: 'A little care adds up.', assistant: 'Let’s help things grow.' };
    byId('page-title').textContent = headings[name] || headings.overview;
    window.scrollTo({ top: 0, behavior: 'smooth' });
}

function openPlantDialog(plant = null) {
    const form = byId('plant-form');
    form.reset();
    byId('plant-form-message').textContent = '';
    byId('plant-id').value = plant?.id || '';
    byId('dialog-title').textContent = plant ? 'Edit plant' : 'Add a plant';
    byId('save-plant-button').textContent = plant ? 'Save changes' : 'Save plant';
    byId('plant-name').value = plant?.name || '';
    byId('plant-species').value = plant?.species || 'Monstera';
    byId('plant-location').value = plant?.location || '';
    byId('plant-interval').value = plant?.wateringIntervalDays || 7;
    byId('plant-last-watered').value = plant?.lastWatered || new Date().toISOString().slice(0, 10);
    byId('plant-image').value = plant?.imageUrl || fallbackImage;
    byId('plant-notes').value = plant?.notes || '';
    byId('plant-dialog').showModal();
}

function openDetails(plant) {
    const entries = history.filter(entry => entry.plantId === plant.id);
    byId('details-title').textContent = plant.name;
    byId('details-content').innerHTML = `
        <img class="detail-photo" data-plant-image src="${escapeHtml(plant.imageUrl || fallbackImage)}" alt="${escapeHtml(plant.species)} foliage">
        <dl class="detail-facts">
            <div><dt>Species</dt><dd>${escapeHtml(plant.species)}</dd></div>
            <div><dt>Location</dt><dd>${escapeHtml(plant.location)}</dd></div>
            <div><dt>Water every</dt><dd>${plant.wateringIntervalDays} days</dd></div>
            <div><dt>Last watered</dt><dd>${dateOnly(plant.lastWatered)}</dd></div>
            <div><dt>Next watering</dt><dd>${dateOnly(plant.nextWateringDate)}</dd></div>
            <div><dt>Care status</dt><dd>${careDue(plant) ? 'Care due' : 'On schedule'}</dd></div>
        </dl>
        <div class="detail-notes">${escapeHtml(plant.notes || 'No notes have been added for this plant.')}</div>
        <h3 class="detail-history-heading">Recent care</h3>
        ${entries.length ? entries.slice(0, 5).map(entry => `<div class="detail-history-item"><strong>${escapeHtml(entry.action)}</strong><span>${dateTime(entry.occurredAt)}${entry.notes ? ` · ${escapeHtml(entry.notes)}` : ''}</span></div>`).join('') : '<div class="empty-state">No care actions have been recorded yet.</div>'}`;
    setImageFallbacks(byId('details-content'));
    byId('details-dialog').showModal();
}

async function savePlant(event) {
    event.preventDefault();
    const form = byId('plant-form');
    if (!form.reportValidity()) return;
    const id = byId('plant-id').value;
    const payload = {
        name: byId('plant-name').value.trim(),
        species: byId('plant-species').value,
        location: byId('plant-location').value.trim(),
        wateringIntervalDays: Number(byId('plant-interval').value),
        lastWatered: byId('plant-last-watered').value,
        imageUrl: byId('plant-image').value,
        notes: byId('plant-notes').value.trim()
    };
    try {
        await api(id ? `/api/plants/${encodeURIComponent(id)}` : '/api/plants', {
            method: id ? 'PUT' : 'POST', body: JSON.stringify(payload)
        });
        byId('plant-dialog').close();
        await loadGarden();
        toast(id ? 'Plant details saved.' : 'Plant added to your garden.');
    } catch (error) {
        byId('plant-form-message').textContent = error.message;
    }
}

async function recordWatering(id) {
    await api(`/api/plants/${encodeURIComponent(id)}/water`, { method: 'POST', body: JSON.stringify({ notes: '' }) });
    await loadGarden();
    toast('Watering recorded. The next reminder is scheduled.');
}

async function completeReminder(id) {
    await api(`/api/reminders/${encodeURIComponent(id)}/complete`, { method: 'POST' });
    await loadGarden();
    toast('Care recorded. The next reminder is scheduled.');
}

async function logout() {
    await api('/api/auth/logout', { method: 'POST' });
    window.location.assign('/login.html');
}

async function askGuide(message) {
    const clean = message.trim();
    if (!clean) return;
    const messages = byId('chat-messages');
    const userMessage = document.createElement('div');
    userMessage.className = 'chat-message user-message';
    userMessage.textContent = clean;
    messages.append(userMessage);
    byId('chat-input').value = '';
    const loading = document.createElement('div');
    loading.className = 'chat-message assistant-message';
    loading.textContent = 'Thinking through your garden...';
    messages.append(loading);
    messages.scrollTop = messages.scrollHeight;
    try {
        const result = await api('/api/assistant', { method: 'POST', body: JSON.stringify({ message: clean }) });
        loading.textContent = result.answer;
    } catch (error) {
        loading.textContent = `I couldn’t answer just now. ${error.message}`;
    }
    messages.scrollTop = messages.scrollHeight;
}

document.addEventListener('DOMContentLoaded', async () => {
    try {
        await refreshCsrf();
        await loadGarden();
    } catch (error) {
        if (!window.location.pathname.endsWith('login.html')) toast(error.message);
    }

    document.addEventListener('click', async event => {
        const addButton = event.target.closest('[data-add-plant]');
        const nav = event.target.closest('[data-view]');
        const waterButton = event.target.closest('[data-water]');
        const editButton = event.target.closest('[data-edit]');
        const detailsButton = event.target.closest('[data-details]');
        const deleteButton = event.target.closest('[data-delete]');
        const completeButton = event.target.closest('[data-complete]');
        const suggestion = event.target.closest('[data-question]');
        try {
            if (addButton) openPlantDialog();
            if (nav) switchView(nav.dataset.view);
            if (waterButton) await recordWatering(waterButton.dataset.water);
            if (editButton) openPlantDialog(plants.find(plant => plant.id === editButton.dataset.edit));
            if (detailsButton) {
                const plant = plants.find(item => item.id === detailsButton.dataset.details);
                if (plant) openDetails(plant);
            }
            if (deleteButton) {
                const plant = plants.find(item => item.id === deleteButton.dataset.delete);
                if (plant && window.confirm(`Remove ${plant.name} and its care history? This cannot be undone.`)) {
                    await api(`/api/plants/${encodeURIComponent(plant.id)}`, { method: 'DELETE' });
                    await loadGarden();
                    toast('Plant removed from your garden.');
                }
            }
            if (completeButton) await completeReminder(completeButton.dataset.complete);
            if (suggestion) {
                switchView('assistant');
                await askGuide(suggestion.dataset.question);
            }
            if (event.target.closest('.logout-button')) await logout();
        } catch (error) {
            toast(error.message);
        }
    });

    byId('plant-form').addEventListener('submit', savePlant);
    byId('close-dialog').addEventListener('click', () => byId('plant-dialog').close());
    byId('cancel-dialog').addEventListener('click', () => byId('plant-dialog').close());
    byId('close-details').addEventListener('click', () => byId('details-dialog').close());
    byId('plant-search').addEventListener('input', applyPlantFilters);
    byId('plant-filter').addEventListener('change', applyPlantFilters);
    byId('chat-form').addEventListener('submit', event => {
        event.preventDefault();
        askGuide(byId('chat-input').value);
    });
});
