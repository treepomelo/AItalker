const key = 'USER_INFO';
export function getUserInfo() { const value = uni.getStorageSync(key); if (!value) return undefined; try { return typeof value === 'string' ? JSON.parse(value) : value; } catch (e) { uni.removeStorageSync(key); return undefined; } }
export function removeUserInfo() { uni.removeStorageSync(key); }
export function setUserInfo(data) { return uni.setStorageSync(key, JSON.stringify(data)); }
