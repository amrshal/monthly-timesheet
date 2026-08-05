document.addEventListener('click', event => {
    if (!event.target.closest('.note-button')) {
        return;
    }

    const button = event.target.closest('.note-button');
    const noteId = button.getAttribute('aria-controls');
    const editor = document.getElementById(noteId);
    if (!editor) {
        return;
    }

    const open = !editor.classList.contains('is-open');
    editor.classList.toggle('is-open', open);
    button.setAttribute('aria-expanded', String(open));
});

document.addEventListener('submit', event => {
    const form = event.target;
    const message = form.getAttribute('data-confirm-message');
    if (message && !window.confirm(message)) {
        event.preventDefault();
    }
});
