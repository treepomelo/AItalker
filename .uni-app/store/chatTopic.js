const key = 'CHAT_TOPIC';
export function getChatTopic() { const value = uni.getStorageSync(key); if (!value) return undefined; try { return typeof value === 'string' ? JSON.parse(value) : value; } catch (e) { uni.removeStorageSync(key); return undefined; } }
export function setChatTopic(data) { return uni.setStorageSync(key, JSON.stringify(data)); }
