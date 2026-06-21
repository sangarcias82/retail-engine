const API_BASE = '/api/v1/products';

async function parseJsonResponse(response) {
    const contentType = response.headers.get('content-type') || '';
    if (contentType.includes('application/json')) {
        return response.json();
    }
    return null;
}

async function apiRequest(url, options = {}) {
    const response = await fetch(url, options);
    const body = await parseJsonResponse(response);
    if (!response.ok) {
        const error = new Error(body?.message || 'Request failed');
        error.status = response.status;
        error.body = body;
        throw error;
    }
    return body;
}

function escapeHtml(value) {
    if (value == null) {
        return '';
    }
    return String(value)
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('"', '&quot;')
        .replaceAll("'", '&#39;');
}

function formatPrice(price) {
    return '$' + Number(price).toFixed(2);
}
