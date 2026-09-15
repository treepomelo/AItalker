const baseHttps = (import.meta.env.VITE_API_BASE_URL || '').trim().replace(/\/+$/, '');

export default {
    baseHttps,
    baseWss: baseHttps.replace(/^http/, 'ws'),
};
