import request from '@/utils/request';

const header = {'X-Config-Request': '1'};
export const getModelSettings = () => request({url: '/admin/model-settings'});
export const saveModelSettings = (channel, data) => request({url: `/admin/model-settings/${channel}`, method: 'PUT', header, data});
export const testModelSettings = (channel, data) => request({url: `/admin/model-settings/${channel}/test`, method: 'POST', header, data, timeout: 260000});
