import request from './../utils/request';

export function reqGetProducts() {
    return request({ url: '/products', method: 'GET' });
}

export function reqGetExplanation(productId) {
    return request({ url: `/products/${productId}/explanations/latest`, method: 'GET' });
}

export function reqGenerateExplanation(productId, data = {}) {
    return request({ url: `/products/${productId}/explanations`, method: 'POST', data });
}

export function reqGenerateAudio(productId, explanationId, data = {}) {
    return request({ url: `/products/${productId}/explanations/${explanationId}/audio`, method: 'POST', data });
}

export const reqGetCapabilities = () => request({url: '/capabilities'});
export const reqGetKnowledge = id => request({url: `/products/${id}/knowledge`});
export const reqGetHistory = id => request({url: `/products/${id}/explanations`});
export const reqGetAudioTask = id => request({url: `/audio-tasks/${id}`});
