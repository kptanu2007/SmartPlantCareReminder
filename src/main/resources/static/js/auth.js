async function csrfToken() {
    const response = await fetch('/api/auth/csrf', { credentials: 'same-origin' });
    if (!response.ok) throw new Error('Could not prepare a secure form. Please refresh and try again.');
    return (await response.json()).token;
}

function setFormMessage(message) {
    const target = document.getElementById('form-message');
    if (target) target.textContent = message || '';
}

async function sendAuthForm(url, payload) {
    const token = await csrfToken();
    const response = await fetch(url, {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': token },
        body: JSON.stringify(payload)
    });
    const result = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(result.message || 'We could not complete that request. Please try again.');
    return result;
}

document.addEventListener('DOMContentLoaded', async () => {
    try {
        const response = await fetch('/api/auth/me', { credentials: 'same-origin' });
        if (response.ok) {
            window.location.replace('/app.html');
            return;
        }
        await csrfToken();
    } catch (error) {
        setFormMessage(error.message);
    }

    const loginForm = document.getElementById('login-form');
    if (loginForm) {
        if (new URLSearchParams(window.location.search).get('registered') === '1') {
            setFormMessage('Your account is ready. Log in to start your garden.');
        }
        loginForm.addEventListener('submit', async event => {
            event.preventDefault();
            setFormMessage('');
            const button = loginForm.querySelector('button[type="submit"]');
            button.disabled = true;
            try {
                await sendAuthForm('/api/auth/login', {
                    login: loginForm.elements.login.value.trim(),
                    password: loginForm.elements.password.value
                });
                window.location.assign('/app.html');
            } catch (error) {
                setFormMessage(error.message);
                button.disabled = false;
            }
        });
    }

    const registerForm = document.getElementById('register-form');
    if (registerForm) {
        registerForm.addEventListener('submit', async event => {
            event.preventDefault();
            setFormMessage('');
            if (!registerForm.reportValidity()) return;
            if (registerForm.elements.password.value !== registerForm.elements.confirmPassword.value) {
                setFormMessage('The passwords do not match.');
                return;
            }
            const button = registerForm.querySelector('button[type="submit"]');
            button.disabled = true;
            try {
                await sendAuthForm('/api/auth/register', {
                    fullName: registerForm.elements.fullName.value.trim(),
                    email: registerForm.elements.email.value.trim(),
                    username: registerForm.elements.username.value.trim(),
                    password: registerForm.elements.password.value,
                    confirmPassword: registerForm.elements.confirmPassword.value
                });
                window.location.assign('/login.html?registered=1');
            } catch (error) {
                setFormMessage(error.message);
                button.disabled = false;
            }
        });
    }
});
