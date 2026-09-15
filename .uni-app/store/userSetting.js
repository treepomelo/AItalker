const key = 'USER_SETTING';
export function getUserSetting() { const value = uni.getStorageSync(key); if (!value) return undefined; try { return typeof value === 'string' ? JSON.parse(value) : value; } catch (e) { uni.removeStorageSync(key); return undefined; } }
export function removeUserSetting() { uni.removeStorageSync(key); }
export function setUserSetting(data) { return uni.setStorageSync(key, JSON.stringify(data)); }
