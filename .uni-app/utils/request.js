import env from '../env';

function service(options = {}) {
    if (!env.baseHttps) {
        return Promise.reject({msg: '服务暂未配置，请联系管理员'});
    }
    options.url = `${env.baseHttps}${options.url}`;
    options.timeout = options.timeout || 100000;
    options.header = {
        'content-type': 'application/json',
        ...options.header,
    };

    return new Promise((resolve, reject) => {
        uni.request({
            ...options,
            success: function (res) {
                if (res.statusCode >= 200 && res.statusCode < 300) {
                    const response = res.data;

                    if (response.code === 200) {
                        resolve(response);
                    } else {
                        reject(response);
                    }
                } else {
                    reject(res.data && typeof res.data === 'object' && res.data.msg
                        ? res.data : {msg: '服务请求失败，请稍后重试'});
                }
            },
            fail: function (e) {
                console.log(e)
                reject('请检查您的网络环境是否正常');
            }
        });
    });
}

export default service;
