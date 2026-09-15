const key = 'TOKEN_VALUE';
export function getTokenValue() { const value = uni.getStorageSync(key); if (!value) return undefined; try { return typeof value === 'string' ? JSON.parse(value) : value; } catch (e) { uni.removeStorageSync(key); return undefined; } }
export function removeTokenValue() { uni.removeStorageSync(key); }
export function setTokenValue(data) { return uni.setStorageSync(key, JSON.stringify(data)); }
