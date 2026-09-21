<template>
  <view class="page">
    <view class="topbar"><view class="back" @click="uni.navigateBack()">‹</view><view class="title">AI 咨询助手</view><view class="more">•••</view></view>
    <scroll-view class="conversation" scroll-y :scroll-into-view="messages.length ? 'msg-'+(messages.length-1) : ''">
      <view v-if="!messages.length" class="welcome"><view class="welcome-card"><image src="/static/avatar/default_gpt.svg" mode="aspectFit"/><view class="welcome-title">你好，我是 AI 助手</view><view class="welcome-subtitle">有什么想了解的，可以和我聊聊</view></view><view class="quick-title">你可以这样问</view><view class="quick-list"><view class="quick" @click="quickSend('请介绍一下这个商品')">请介绍一下这个商品 <text>›</text></view><view class="quick" @click="quickSend('这个商品适合什么场景？')">这个商品适合什么场景？ <text>›</text></view></view></view>
      <view v-if="capabilities && !capabilities.modelConfigured" class="notice">尚未配置大模型，请管理员完成配置后重试。</view>
      <view v-for="(m,index) in messages" :id="'msg-'+index" :key="index" class="message-row" :class="m.role">
        <image v-if="m.role==='assistant'" class="avatar" src="/static/avatar/default_gpt.svg" mode="aspectFit"/>
        <view class="bubble-wrap"><view class="bubble">{{ m.content || '正在连接…' }}</view><view v-if="m.role==='assistant'" class="source">内容由 AI 生成</view></view>
      </view>
      <view v-if="error" class="notice error">{{ error }}</view>
      <view id="composer-anchor" class="anchor"/>
    </scroll-view>
    <view class="composer"><view class="mic">♩</view><textarea v-model="input" class="input" placeholder="输入你想聊的内容…" :maxlength="4000" :disabled="generating" auto-height/><view class="send" :class="{disabled:generating || !input.trim()}" @click="send">↑</view></view>
    <view v-if="generating" class="stop-link" @click="stop">停止生成</view><view v-else class="new-link" @click="newChat">新建会话</view>
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
function quickSend(text){input.value=text;send()}
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
.page{background:linear-gradient(180deg,#f5f8ff 0%,#f3f6fc 45%,#eef2f8 100%);color:#18283c;min-height:100vh;width:100%;overflow:hidden;box-sizing:border-box}.topbar{position:fixed;z-index:10;top:0;left:0;right:0;height:112rpx;background:rgba(255,255,255,.98);display:flex;align-items:center;justify-content:space-between;padding:0 24rpx;box-sizing:border-box;border-bottom:1rpx solid #e5ebf4;box-shadow:0 3rpx 14rpx rgba(42,70,110,.05);overflow:hidden}.title{font-size:40rpx;font-weight:700;color:#14619b;letter-spacing:1rpx;white-space:nowrap}.back{font-size:76rpx;font-weight:200;color:#3c4149;line-height:60rpx;width:64rpx;flex:none}.more{font-size:30rpx;letter-spacing:4rpx;color:#4e5157;width:64rpx;flex:none;text-align:center;white-space:nowrap}.conversation{position:fixed;top:112rpx;bottom:112rpx;left:0;width:100%;padding:30rpx 24rpx 40rpx;box-sizing:border-box;overflow-x:hidden}.welcome{padding:10rpx 0 38rpx}.welcome-card{background:rgba(255,255,255,.8);border:1rpx solid #e4ebf5;border-radius:28rpx;padding:30rpx;text-align:center;box-shadow:0 8rpx 24rpx rgba(57,86,125,.06);box-sizing:border-box}.welcome image{display:block;width:100rpx;height:100rpx;margin:0 auto 10rpx}.welcome-title{font-size:31rpx;font-weight:650;color:#20344d}.welcome-subtitle{font-size:24rpx;color:#8290a2;margin-top:8rpx}.quick-title{font-size:23rpx;color:#8996a7;margin:28rpx 6rpx 12rpx}.quick-list{display:flex;flex-direction:column;gap:12rpx}.quick{background:#fff;border:1rpx solid #e4eaf2;border-radius:16rpx;padding:22rpx 24rpx;font-size:25rpx;color:#36516e;box-shadow:0 3rpx 10rpx rgba(57,86,125,.04);box-sizing:border-box;width:100%;overflow:hidden}.quick text{float:right;font-size:34rpx;color:#9aa9bb;line-height:25rpx}.message-row{display:flex;align-items:flex-start;margin:26rpx 0;max-width:100%;box-sizing:border-box}.message-row.user{justify-content:flex-end}.avatar{width:72rpx;height:72rpx;border-radius:50%;background:#dbe9ff;margin:4rpx 16rpx 0 0;padding:10rpx;box-sizing:border-box;flex:none}.bubble-wrap{max-width:calc(100% - 88rpx);min-width:0}.bubble{box-sizing:border-box;max-width:100%;padding:25rpx 28rpx;border-radius:28rpx;background:#fff;box-shadow:0 4rpx 14rpx rgba(41,65,95,.08);font-size:29rpx;line-height:1.75;white-space:pre-wrap;word-break:break-all;overflow-wrap:anywhere}.user .bubble-wrap{max-width:88%}.user .bubble{background:#cfe0fb;border-top-right-radius:8rpx}.assistant .bubble{border-top-left-radius:8rpx}.source{font-size:21rpx;color:#a0a9b5;margin:10rpx 8rpx}.notice{background:#fff4dc;padding:20rpx;border-radius:14rpx;margin:20rpx 0;font-size:24rpx;line-height:1.7;box-sizing:border-box;max-width:100%}.error{color:#984d35}.anchor{height:24rpx}.composer{position:fixed;z-index:11;left:0;right:0;bottom:42rpx;background:rgba(255,255,255,.98);border-top:1rpx solid #e1e8f1;padding:18rpx 20rpx 10rpx;display:flex;align-items:flex-end;gap:10rpx;box-sizing:border-box;box-shadow:0 -5rpx 18rpx rgba(45,68,98,.05)}.mic{width:62rpx;height:62rpx;border-radius:50%;background:#f0f3f8;color:#68778a;text-align:center;line-height:62rpx;font-size:36rpx;flex:none}.input{flex:1;min-width:0;background:#f1f4f8;border-radius:34rpx;padding:18rpx 22rpx;box-sizing:border-box;min-height:62rpx;max-height:180rpx;font-size:27rpx;line-height:1.45}.send{width:62rpx;height:62rpx;border-radius:50%;background:#4e5e73;color:#fff;text-align:center;line-height:62rpx;font-size:40rpx;font-weight:600;flex:none}.send.disabled{background:#cbd2dc}.stop-link,.new-link{position:fixed;z-index:12;left:0;right:0;bottom:0;text-align:center;background:#fff;color:#8a96a5;font-size:22rpx;height:42rpx;line-height:38rpx}@media(min-width:900px){.conversation{padding-left:calc((100vw - 800px)/2);padding-right:calc((100vw - 800px)/2)}}
</style>
