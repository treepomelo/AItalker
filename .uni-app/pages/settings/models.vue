<template>
  <view class="page">
    <view class="eyebrow">连接你的 AI 服务</view>
    <view class="title">模型配置</view>
    <view class="intro">聊天与语音独立配置。保存后立即生效，无需重启；测试会使用当前表单发起少量真实请求，可能产生费用。</view>
    <button v-if="ready" size="mini" :disabled="!!busy" @click="load">重新读取（放弃未保存修改）</button>
    <view v-if="loadError" class="notice error">{{ loadError }}<button size="mini" @click="load">重新加载</button></view>
    <view v-if="!ready && !loadError" class="notice">正在读取服务端配置…</view>
    <view v-if="ready" class="grid">
      <view v-for="channel in channels" :key="channel.id" class="panel">
        <view class="heading"><text>{{ channel.title }}</text><text class="badge">{{ state[channel.id].active.configured?'已配置':'未配置' }}</text></view>
        <view class="muted">{{ channel.description }}</view>
        <view class="field-label">厂商模板</view>
        <picker :disabled="!!busy" :range="presets[channel.id]" range-key="name" @change="usePreset(channel.id, Number($event.detail.value))"><view class="select">{{ state[channel.id].presetName || '选择厂商模板（可选）' }} ▾</view></picker>
        <view class="field-label">接口协议</view>
        <picker :disabled="!!busy" :range="protocols[channel.id]" range-key="name" @change="changeProtocol(channel.id, Number($event.detail.value))"><view class="select">{{ protocolName(channel.id) }} ▾</view></picker>
        <view class="help">{{ protocolHelp(state[channel.id].form.protocol) }}</view>
        <view class="field-label">服务地址</view>
        <input v-model="state[channel.id].form.baseUrl" :disabled="!!busy" class="input" placeholder="https://api.example.com/v1" :maxlength="1000" />
        <view class="help">填写根地址或对应的完整接口地址。保留厂商要求的版本前缀，不在 URL 内填写 Key。</view>
        <view class="field-label">API Key <text class="muted">{{ state[channel.id].active.hasApiKey?'已保存，留空保持原密钥':'尚未保存密钥' }}</text></view>
        <input v-model="state[channel.id].form.apiKey" :disabled="!!busy || state[channel.id].clearKey" password class="input" placeholder="输入新 Key，不会回显或保存到浏览器" :maxlength="4096" />
        <view class="clear-key"><switch :checked="state[channel.id].clearKey" :disabled="!!busy" color="#456c58" @change="state[channel.id].clearKey=$event.detail.value"/><text>清除已保存的 Key（保存后停用）</text></view>
        <view class="field-label">模型 ID</view>
        <input v-model="state[channel.id].form.model" :disabled="!!busy" class="input" placeholder="填写厂商实际支持的模型或部署 ID" :maxlength="200" />
        <view v-if="channel.id==='speech'">
          <view class="field-label">音色 ID</view><input v-model="state[channel.id].form.voice" :disabled="!!busy" class="input" placeholder="填写该模型支持的音色 ID" :maxlength="200" />
          <view class="pair"><view><view class="field-label">语速</view><input v-model="state[channel.id].form.speed" :disabled="!!busy" type="digit" class="input" /></view><view><view class="field-label">文本上限（字）</view><input v-model="state[channel.id].form.maxCharacters" :disabled="!!busy" type="number" class="input" /></view></view>
          <view class="help">统一输出 MP3。MiniMax 语速 0.5–2；OpenAI 兼容语速 0.25–4，以所选模型支持范围为准。</view>
        </view>
        <view class="pair"><view><view class="field-label">超时（秒）</view><input v-model="state[channel.id].form.timeoutSeconds" :disabled="!!busy" type="number" class="input" /></view><view v-if="channel.id==='chat'"><view class="field-label">输出 Token 上限</view><input v-model="state[channel.id].form.maxTokens" :disabled="!!busy" type="number" class="input" /></view></view>
        <view v-if="channel.id==='chat' && state.chat.form.protocol==='OPENAI_CHAT'">
          <view class="field-label">输出长度参数</view>
          <picker :disabled="!!busy" :range="tokenFields" @change="state.chat.form.outputLimitField=tokenFields[$event.detail.value]"><view class="select">{{ state.chat.form.outputLimitField }} ▾</view></picker>
          <view class="help">按模型文档选择 max_tokens 或 max_completion_tokens；omit 表示不发送长度参数。</view>
          <view class="field-label">扩展参数（JSON，可选）</view><textarea v-model="state.chat.form.extraBody" :disabled="!!busy" class="json" :maxlength="4000" auto-height />
          <view class="help">例如 {"enable_thinking":false}。支持采样与思考参数；模型、鉴权、消息、流式开关不可覆盖。</view>
        </view>
        <view class="notice" :class="{warning:dirty(channel.id)}">{{ dirty(channel.id)?'有未保存的修改，当前调用仍使用已保存配置。':'表单与当前生效配置一致。' }}</view>
        <view class="actions"><button :disabled="!!busy" class="primary" @click="save(channel.id)">{{ busy===channel.id+'save'?'保存中…':'保存并应用' }}</button><button :disabled="!!busy" @click="test(channel.id)">{{ busy===channel.id+'test'?'正在测试…':(channel.id==='chat'?'测试普通 + 流式':'测试语音接口') }}</button></view>
        <view v-if="state[channel.id].error" class="notice error">{{ state[channel.id].error }}</view>
        <view v-if="state[channel.id].success && !dirty(channel.id)" class="notice success">{{ state[channel.id].success }}</view>
        <view v-if="verification(channel.id).status !== 'UNTESTED'" class="verification">
          <view>{{ verification(channel.id).status==='PASSED'?'✓ 验证通过':'验证未通过' }}<text v-if="dirty(channel.id)" class="muted"> · 草稿结果，保存后才生效</text></view>
          <view v-for="(check,name) in verification(channel.id).checks" :key="name" class="check" :class="{errorText:!check.passed}">{{ check.passed?'✓':'×' }} {{ name }}：{{ check.detail }}</view>
          <view class="help">{{ verification(channel.id).checkedAt }} · {{ verification(channel.id).elapsedMs }} ms</view>
        </view>
        <view v-else class="help">当前参数尚未验证。修改参数会使旧验证结果失效。</view>
        <view class="endpoint"><view class="field-label">已保存配置的实际请求地址</view><text>{{ state[channel.id].active.endpoint || '未设置地址' }}</text><text v-if="state[channel.id].active.streamEndpoint && state[channel.id].active.streamEndpoint!==state[channel.id].active.endpoint">流式：{{ state[channel.id].active.streamEndpoint }}</text></view>
      </view>
    </view>
    <view class="panel footnote"><view class="heading">兼容范围</view><view>聊天：OpenAI 兼容 Chat Completions、Claude Messages、Gemini GenerateContent。语音：OpenAI 兼容 MP3、MiniMax T2A v2 hex。</view><view class="muted">DeepSeek、通义千问、硅基流动、豆包 Ark 可选择各自的 OpenAI 兼容聊天地址。厂商名称相同不代表所有接口兼容；Azure 专用鉴权、OpenAI Responses、豆包或讯飞原生语音尚未适配。没有真实 Key 时，只能验证本地协议适配，不能证明账号权限或模型可用。</view></view>
  </view>
</template>

<script setup>
import {ref,reactive} from 'vue';
import {onShow} from '@dcloudio/uni-app';
import {getModelSettings,saveModelSettings,testModelSettings} from '@/api/modelSettings';
const ready=ref(false),loadError=ref(''),busy=ref(''),revision=ref(0);
const channels=[{id:'chat',title:'聊天与讲解模型',description:'实时聊天与商品讲解共用此模型配置，分别验证流式和普通回复。'},{id:'speech',title:'语音模型',description:'独立配置文字转语音。测试检查实际 MP3 音频，不影响现有讲解。'}];
const protocols={chat:[{id:'OPENAI_CHAT',name:'OpenAI 兼容 · Chat Completions'},{id:'ANTHROPIC',name:'Claude 原生 · Messages'},{id:'GEMINI',name:'Gemini 原生 · GenerateContent'}],speech:[{id:'OPENAI_SPEECH',name:'OpenAI 兼容 · Audio Speech'},{id:'MINIMAX_SPEECH',name:'MiniMax 原生 · T2A v2'}]};
const presets={chat:[{name:'OpenAI',protocol:'OPENAI_CHAT',baseUrl:'https://api.openai.com/v1',outputLimitField:'max_completion_tokens'},{name:'DeepSeek（兼容接口）',protocol:'OPENAI_CHAT',baseUrl:'https://api.deepseek.com/v1'},{name:'通义千问（中国区兼容接口）',protocol:'OPENAI_CHAT',baseUrl:'https://dashscope.aliyuncs.com/compatible-mode/v1'},{name:'硅基流动（兼容接口）',protocol:'OPENAI_CHAT',baseUrl:'https://api.siliconflow.cn/v1'},{name:'豆包 Ark（兼容接口）',protocol:'OPENAI_CHAT',baseUrl:'https://ark.cn-beijing.volces.com/api/v3'},{name:'Anthropic Claude',protocol:'ANTHROPIC',baseUrl:'https://api.anthropic.com/v1'},{name:'Google Gemini',protocol:'GEMINI',baseUrl:'https://generativelanguage.googleapis.com/v1beta'}],speech:[{name:'OpenAI Speech',protocol:'OPENAI_SPEECH',baseUrl:'https://api.openai.com/v1',voice:'alloy'},{name:'硅基流动（兼容语音）',protocol:'OPENAI_SPEECH',baseUrl:'https://api.siliconflow.cn/v1',voice:''},{name:'MiniMax（中国区）',protocol:'MINIMAX_SPEECH',baseUrl:'https://api.minimaxi.com/v1',voice:''},{name:'MiniMax（国际区）',protocol:'MINIMAX_SPEECH',baseUrl:'https://api.minimax.io/v1',voice:''}]};
const tokenFields=['max_tokens','max_completion_tokens','omit'];
const state=reactive({chat:{form:{},active:{},baseline:'',clearKey:false,presetName:'',error:'',success:'',testResult:null,testSignature:''},speech:{form:{},active:{},baseline:'',clearKey:false,presetName:'',error:'',success:'',testResult:null,testSignature:''}});
const fields=['protocol','baseUrl','apiKey','model','voice','speed','timeoutSeconds','maxTokens','maxCharacters','outputLimitField','extraBody'];
function payload(id){const s=state[id],f=s.form;return {revision:revision.value,clearKey:s.clearKey,config:Object.fromEntries(fields.map(k=>[k,['speed','timeoutSeconds','maxTokens','maxCharacters'].includes(k)?Number(f[k]):f[k]]))}}
function signature(id){const p=payload(id);return JSON.stringify({config:p.config,clearKey:p.clearKey})}
function dirty(id){return ready.value && signature(id)!==state[id].baseline}
function accept(data,target){for(const id of ['chat','speech']){const keep=target&&target!==id&&dirty(id);state[id].active=data[id];if(!keep){state[id].form=Object.fromEntries(fields.map(k=>[k,k==='apiKey'?'':data[id][k]]));state[id].clearKey=false;state[id].baseline=signature(id);state[id].testResult=null;state[id].testSignature=''}}revision.value=data.revision;ready.value=true}
async function load(){if(busy.value)return;loadError.value='';busy.value='load';try{accept((await getModelSettings()).data);for(const id of ['chat','speech']){resetFeedback(id);state[id].presetName=''}}catch(e){loadError.value=e?.msg||'无法加载配置，请检查管理权限'}finally{busy.value=''}}
onShow(()=>{if(!ready.value)load()});
function protocolName(id){return protocols[id].find(p=>p.id===state[id].form.protocol)?.name||'请选择协议'}
function protocolHelp(p){return {OPENAI_CHAT:'Bearer 鉴权；解析 choices.message / choices.delta。',ANTHROPIC:'x-api-key + anthropic-version；系统提示与 messages 分开。',GEMINI:'x-goog-api-key；转换 contents / parts，并独立生成流式地址。',OPENAI_SPEECH:'Bearer 鉴权；返回 MP3 二进制音频。',MINIMAX_SPEECH:'Bearer 鉴权；转换 voice_setting 并解码 hex 音频。'}[p]||''}
function resetFeedback(id){state[id].error='';state[id].success='';state[id].testResult=null}
function usePreset(id,index){const p=presets[id][index];Object.assign(state[id].form,{protocol:p.protocol,baseUrl:p.baseUrl,apiKey:'',model:'',voice:p.voice||'',extraBody:'{}',outputLimitField:p.outputLimitField||'max_tokens'});state[id].presetName=p.name;state[id].clearKey=false;resetFeedback(id)}
function changeProtocol(id,index){state[id].form.protocol=protocols[id][index].id;state[id].form.extraBody='{}';state[id].presetName='';resetFeedback(id)}
function verification(id){const s=state[id];if(s.testResult&&s.testSignature===signature(id))return s.testResult;if(!dirty(id))return s.active.verification||{status:'UNTESTED'};return {status:'UNTESTED'}}
async function save(id){busy.value=id+'save';resetFeedback(id);try{const result=await saveModelSettings(id,payload(id));accept(result.data,id);state[id].success='已保存并应用。后续调用使用新配置，已开始的任务保持原配置。'}catch(e){state[id].error=e?.msg||'保存失败，请重试'}finally{busy.value=''}}
async function test(id){busy.value=id+'test';resetFeedback(id);const sig=signature(id);try{const r=await testModelSettings(id,payload(id));state[id].testResult=r.data;state[id].testSignature=sig}catch(e){state[id].error=e?.msg||'测试失败，请检查配置'}finally{busy.value=''}}
</script>

<style scoped>
.page{min-height:100vh;box-sizing:border-box;padding:38rpx 32rpx 90rpx;background:#f5f2eb;color:#263d36}.eyebrow{font-size:22rpx;color:#778078;letter-spacing:3rpx}.title{font-size:46rpx;font-weight:650;margin:14rpx 0}.intro{font-size:26rpx;line-height:1.8;color:#748074;margin-bottom:32rpx}.grid{display:grid;grid-template-columns:1fr 1fr;gap:28rpx}.panel{background:#fffdf8;border:1rpx solid #e5e2d8;border-radius:20rpx;padding:30rpx;min-width:0}.heading{display:flex;align-items:center;justify-content:space-between;font-size:31rpx;font-weight:650;margin-bottom:16rpx}.badge{font-size:22rpx;font-weight:400;color:#75836d;background:#edf1e7;padding:8rpx 16rpx;border-radius:8rpx}.muted,.help{font-size:22rpx;line-height:1.8;color:#7c8679}.help{margin-top:10rpx}.field-label{font-size:25rpx;margin:26rpx 0 12rpx}.input,.select,.json{box-sizing:border-box;width:100%;padding:18rpx 20rpx;border:1rpx solid #dce0d4;border-radius:10rpx;background:white;font-size:25rpx;height:auto;min-height:72rpx}.json{min-height:110rpx;font-family:monospace;line-height:1.6}.select{cursor:pointer}.clear-key{display:flex;align-items:center;font-size:22rpx;color:#7a8477;margin-top:14rpx}.clear-key switch{transform:scale(.7);transform-origin:left center;width:88rpx}.pair{display:flex;gap:16rpx}.pair>view{flex:1;min-width:0}.notice{padding:18rpx 22rpx;background:#eef1e8;border-radius:10rpx;font-size:24rpx;line-height:1.8;margin:24rpx 0 18rpx}.warning{background:#f8f0db}.error{background:#faeae3;color:#994b35}.success{background:#e6f0e5}.actions{display:flex;gap:12rpx}.actions button{flex:1;font-size:24rpx;margin:0;background:#e9eee3;color:#365b45}.actions .primary{background:#365b45;color:white}.verification{margin-top:26rpx;padding:20rpx;border:1rpx solid #dce1d2;border-radius:10rpx;font-size:25rpx}.check{font-size:23rpx;line-height:1.8;margin-top:10rpx}.errorText{color:#9b4b38}.endpoint{margin-top:20rpx}.endpoint text{display:block;word-break:break-all;font-family:monospace;font-size:22rpx;color:#697a69;line-height:1.8}.footnote{margin-top:28rpx;font-size:25rpx;line-height:1.9}@media(min-width:1200px){.page{padding-left:calc((100vw - 1120px)/2);padding-right:calc((100vw - 1120px)/2)}}@media(max-width:760px){.grid{grid-template-columns:1fr}.actions{flex-wrap:wrap}}
</style>
