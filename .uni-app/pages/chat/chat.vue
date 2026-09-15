<template>
  <view class="page">
    <view class="title">实时聊天</view>
    <view class="muted">与大模型实时对话。此会话与商品讲解相互独立。</view>
    <view v-if="capabilities && !capabilities.modelConfigured" class="notice">尚未配置大模型，请管理员完成配置后重试。</view>
    <view v-for="(m,index) in messages" :key="index" class="message" :class="m.role">
      <view class="role">{{ m.role==='user'?'你':'AI 助手' }}</view><text>{{ m.content || '正在连接…' }}</text>
    </view>
    <view v-if="error" class="notice error">{{ error }}</view>
    <textarea v-model="input" class="input" placeholder="输入你想聊的内容" :maxlength="4000" :disabled="generating" auto-height />
    <view class="actions"><button :disabled="generating || !input.trim()" @click="send">发送</button><button v-if="generating" @click="stop">停止生成</button><button v-else @click="newChat">新建会话</button></view>
  </view>
</template>
<script setup>
import {ref,onMounted,onBeforeUnmount} from 'vue';
import {onHide,onShow} from '@dcloudio/uni-app';
import {getTokenValue} from '@/store/token';
import {reqGetCapabilities} from '@/api/product';
import env from '@/env';
const messages=ref([]),input=ref(''),error=ref(''),capabilities=ref(null),generating=ref(false);
let socket,requestId=0;
onShow(async()=>{try{capabilities.value=(await reqGetCapabilities()).data}catch(e){error.value=e?.msg||'无法读取模型状态'}});
function stop(){requestId++;if(socket){socket.close();socket=null}generating.value=false}
function newChat(){stop();messages.value=[];error.value=''}
async function send(){
  if(generating.value||!input.value.trim())return;
  error.value='';generating.value=true;const current=++requestId;
  try{const result=await reqGetCapabilities();if(current!==requestId)return;capabilities.value=result.data}catch(e){if(current===requestId){error.value=e?.msg||'无法读取模型状态';generating.value=false}return}
  if(!capabilities.value.modelConfigured){error.value='尚未配置大模型，请管理员完成配置后重试';generating.value=false;return}
  const token=getTokenValue()||'local';
  messages.value.push({role:'user',content:input.value.trim()});input.value='';
  const payload=JSON.stringify(messages.value.filter(m=>m.content).slice(-28));
  const answer={role:'assistant',content:''};messages.value.push(answer);const answerIndex=messages.value.length-1;

  socket=uni.connectSocket({url:env.baseWss+'/text-chat/'+encodeURIComponent(token),complete:()=>{}});
  socket.onOpen(()=>{if(current===requestId)socket.send({data:payload})});
  socket.onMessage(res=>{if(current!==requestId)return;try{const event=JSON.parse(res.data);if(event.type==='delta')messages.value[answerIndex].content+=event.content;if(event.type==='error'){error.value=event.content;generating.value=false}if(event.type==='done')generating.value=false}catch(e){error.value='模型响应格式异常';stop()}});
  socket.onError(()=>{if(current===requestId){error.value='聊天连接失败，请检查本地服务和模型配置';generating.value=false}});
  socket.onClose(()=>{if(current===requestId){if(generating.value)error.value='连接已关闭，可重新发送';generating.value=false;socket=null}});
}
onHide(stop);onBeforeUnmount(stop);
</script>
<style scoped>
.page{background:#f5f2eb;color:#263d36;min-height:100vh;padding:36rpx;box-sizing:border-box}.title{font-size:44rpx;font-weight:650;margin-bottom:16rpx}.muted{font-size:25rpx;color:#788679;line-height:1.8}.notice{background:#f5ead2;padding:24rpx;border-radius:14rpx;margin:24rpx 0;font-size:26rpx;line-height:1.8}.error{color:#984d35}.message{margin:28rpx 0;padding:28rpx;border-radius:16rpx;background:#fffdf8;font-size:28rpx;line-height:1.9;white-space:pre-wrap}.message.user{background:#e7eddf}.role{font-size:22rpx;color:#7a887d;margin-bottom:12rpx}.input{background:#fffdf8;border:1rpx solid #d9ded3;border-radius:14rpx;padding:24rpx;box-sizing:border-box;width:100%;min-height:180rpx;margin-top:32rpx}.actions{display:flex;gap:16rpx;margin-top:24rpx}.actions button{margin:0;font-size:26rpx;background:#345747;color:white}@media(min-width:900px){.page{padding-left:calc((100vw - 800px)/2);padding-right:calc((100vw - 800px)/2)}}
</style>
