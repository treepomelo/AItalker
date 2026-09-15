<template>
  <view class="page">
    <view class="eyebrow">青禾文创 · 产品体验室</view>
    <view class="title">让好物的故事，被听见</view>
    <view class="subtitle">选择一件文创商品，查看资料、生成讲解，或试听 AI 语音。</view>
    <view v-if="loadError" class="notice error">{{ loadError }}<button size="mini" @click="load">重新加载</button></view>
    <view v-if="!products.length && !loadError" class="notice">正在加载商品…</view>
    <view class="products">
      <view v-for="(p,index) in products" :key="p.id" class="product-card" :class="{active:selected?.id===p.id}" @click="selectProduct(p)">
        <view class="number">0{{ index+1 }} / 测试商品</view>
        <view class="product-name">{{ p.name }}</view>
        <view class="muted">{{ p.description }}</view>
        <view class="price">¥ {{ p.details?.price || '—' }} <text class="muted">演示价格</text></view>
      </view>
    </view>
    <view v-if="selected" class="columns">
      <view class="panel facts">
        <view class="section-title">商品档案</view>
        <view class="field"><text class="label">材质</text><text>{{ selected.details?.material }}</text></view>
        <view class="field"><text class="label">规格</text><text>{{ selected.details?.specification }}</text></view>
        <view class="field"><text class="label">包装</text><text>{{ selected.details?.packaging }}</text></view>
        <view class="field"><text class="label">养护</text><text>{{ selected.details?.care }}</text></view>
        <view class="section-title spaced">知识资料 · {{ documents.length }} 份</view>
        <view v-for="doc in documents" :key="doc.id" class="document" @click="openedDoc=openedDoc===doc.id?'':doc.id">
          <view>{{ doc.name }} <text class="muted">{{ openedDoc===doc.id?'收起':'查看' }}</text></view>
          <text v-if="openedDoc===doc.id" class="document-text">{{ doc.content }}</text>
        </view>
        <view class="disclaimer">{{ selected.details?.disclaimer }}</view>
      </view>
      <view class="panel workbench">
        <view class="section-title">商品讲解</view>
        <view v-if="capabilities && !capabilities.modelConfigured" class="notice">尚未配置大模型。你可以先阅读示例讲解；配置模型后即可生成新版本。</view>
        <view class="options">
          <picker :range="scenarios" @change="scenario=scenarios[$event.detail.value]"><view class="option">场景 · {{ scenario }}</view></picker>
          <picker :range="tones" @change="tone=tones[$event.detail.value]"><view class="option">语气 · {{ tone }}</view></picker>
          <picker :range="durations" @change="duration=durations[$event.detail.value]"><view class="option">时长 · {{ duration }} 秒</view></picker>
        </view>
        <button class="primary" :disabled="loading" @click="generate">{{ loading?'正在生成讲解…':'生成新讲解' }}</button>
        <view v-if="generationError" class="notice error">{{ generationError }}</view>
        <view v-if="history.length" class="history">
          <picker :range="history" range-key="label" @change="chooseVersion(history[$event.detail.value])"><view>查看历史 · {{ explanation?.isDemo?'人工示例':('版本 '+explanation?.version) }} ▾</view></picker>
        </view>
        <view v-if="explanation" class="explanation">
          <view class="badge">{{ explanation.isDemo?'人工编写的示例讲解 · 非模型生成':'AI 生成讲解 · 请核对商品资料' }}</view>
          <text class="script">{{ explanation.content }}</text>
          <view class="sources">资料来源：{{ (explanation.sources || []).map(s=>s.name).join('、') }}</view>
        </view>
        <view v-else class="notice">暂无讲解，选择参数后点击生成。</view>
        <view class="qa">
          <view class="section-title">向 AI 提问</view>
          <view class="muted">回答仅依据当前商品的知识资料，不确定的信息会明确说明。</view>
          <textarea v-model="question" class="question-input" maxlength="1000" auto-height placeholder="例如：这件商品适合怎样的使用场景？" :disabled="questionLoading" />
          <button class="ask-button" :disabled="questionLoading || !question.trim() || !capabilities?.modelConfigured" @click="askProduct">{{ questionLoading?'正在回答…':'发送问题' }}</button>
          <view v-if="questionError" class="notice error">{{ questionError }}</view>
          <view v-if="answer" class="answer"><view class="badge">AI 商品问答</view><text class="script">{{ answer }}</text></view>
        </view>
        <view class="speech">
          <view class="section-title">语音讲解</view>
          <view class="muted">音频由 AI 合成。语音与文字独立生成，失败不会丢失讲解。</view>
          <view v-if="capabilities && !capabilities.speechConfigured" class="notice">语音服务尚未配置。</view>
          <view class="audio-actions">
            <button :disabled="!explanation || speechLoading" @click="generateSpeech">{{ speechLoading?speechStatus:'生成 / 获取语音' }}</button>
            <button v-if="audioUrl" @click="play">{{ playing?'暂停':(paused?'继续播放':'播放语音') }}</button>
            <button v-if="audioUrl" @click="stopAudio">停止</button>
          </view>
          <view v-if="speechError" class="notice error">{{ speechError }}</view>
          <view v-if="audioUrl" class="muted">{{ playing?'正在播放':(paused?'已暂停':'音频已就绪') }}</view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import {ref, onMounted, onBeforeUnmount} from 'vue';
import {onHide,onShow} from '@dcloudio/uni-app';
import env from '@/env';
import {getTokenValue} from '@/store/token';
import {reqGetProducts,reqGenerateExplanation,reqGenerateAudio,reqGetCapabilities,reqGetKnowledge,reqGetHistory,reqGetAudioTask} from '@/api/product';
const products=ref([]),selected=ref(null),documents=ref([]),history=ref([]),explanation=ref(null),capabilities=ref(null);
const loadError=ref(''),generationError=ref(''),speechError=ref(''),loading=ref(false),speechLoading=ref(false),speechStatus=ref('等待生成…');
const audioUrl=ref(''),playing=ref(false),paused=ref(false),openedDoc=ref('');
const question=ref(''),answer=ref(''),questionError=ref(''),questionLoading=ref(false);
const scenarios=['门店导购','直播介绍','礼物推荐'],tones=['自然亲切','文化讲述','简洁专业'],durations=[30,60,90,120];
const scenario=ref(scenarios[0]),tone=ref(tones[0]),duration=ref(60);
let epoch=0,pollTimer,audio,questionSocket,questionEpoch=0;
const message=(e,fallback)=>e?.msg || e?.message || (typeof e==='string'?e:fallback);
function stopAudio(){if(audio){audio.stop();audio.destroy();audio=null}playing.value=false;paused.value=false}
function stopQuestion(){questionEpoch++;if(questionSocket){questionSocket.close();questionSocket=null}questionLoading.value=false}
function cancelPolling(){clearTimeout(pollTimer);pollTimer=undefined;speechLoading.value=false}
function chooseVersion(version){epoch++;loading.value=false;cancelPolling();stopAudio();audioUrl.value='';speechError.value='';explanation.value=version}
async function load(){
  loadError.value='';
  try{const [p,c]=await Promise.all([reqGetProducts(),reqGetCapabilities()]);products.value=p.data;capabilities.value=c.data;if(products.value.length)await selectProduct(products.value[0]);else loadError.value='暂无商品资料'}
  catch(e){loadError.value=message(e,'商品加载失败，请重试')}
}
async function selectProduct(p){
  const current=++epoch;cancelPolling();stopAudio();stopQuestion();question.value='';answer.value='';questionError.value='';selected.value=p;documents.value=[];history.value=[];explanation.value=null;audioUrl.value='';openedDoc.value='';generationError.value='';speechError.value='';loading.value=false;
  try{const [d,h]=await Promise.all([reqGetKnowledge(p.id),reqGetHistory(p.id)]);if(current!==epoch)return;documents.value=d.data;history.value=h.data.map(e=>({...e,label:e.isDemo?'人工示例讲解':`版本 ${e.version} · ${e.createdAt}`}));explanation.value=history.value[0]||null}
  catch(e){if(current===epoch)generationError.value=message(e,'商品资料加载失败')}
}
async function generate(){
  if(!selected.value||loading.value)return;
  const current=epoch;loading.value=true;generationError.value='';
  try{const r=await reqGenerateExplanation(selected.value.id,{scenario:scenario.value,tone:tone.value,duration:duration.value});if(current!==epoch)return;epoch++;loading.value=false;cancelPolling();stopAudio();audioUrl.value='';speechError.value='';const e={...r.data,label:`版本 ${r.data.version} · ${r.data.createdAt}`};explanation.value=e;history.value.unshift(e)}
  catch(e){if(current===epoch)generationError.value=message(e,'讲解生成失败，请重试')}
  finally{if(current===epoch)loading.value=false}
}
function askProduct(){
  if(!selected.value||questionLoading.value||!question.value.trim())return;
  stopQuestion();const current=questionEpoch;questionLoading.value=true;questionError.value='';answer.value='';
  const token=getTokenValue()||'local';
  questionSocket=uni.connectSocket({url:env.baseWss+'/product-chat/'+encodeURIComponent(token)+'/'+selected.value.id,complete:()=>{}});
  questionSocket.onOpen(()=>{if(current===questionEpoch)questionSocket.send({data:JSON.stringify({productId:selected.value.id,question:question.value.trim()})})});
  questionSocket.onMessage(res=>{if(current!==questionEpoch)return;try{const event=JSON.parse(res.data);if(event.type==='delta')answer.value+=event.content;if(event.type==='error'){questionError.value=event.content;questionLoading.value=false}if(event.type==='done')questionLoading.value=false}catch(e){questionError.value='模型响应格式异常';stopQuestion()}});
  questionSocket.onError(()=>{if(current===questionEpoch){questionError.value='提问连接失败，请检查本地服务和模型配置';questionLoading.value=false}});
  questionSocket.onClose(()=>{if(current===questionEpoch){questionLoading.value=false;questionSocket=null}});
}
async function generateSpeech(){
  if(!explanation.value||speechLoading.value)return;
  const current=epoch; speechLoading.value=true;speechError.value='';speechStatus.value='正在创建语音…';
  try{const r=await reqGenerateAudio(selected.value.id,explanation.value.id);if(current!==epoch)return;await poll(r.data,current,Date.now()+240000)}
  catch(e){if(current===epoch){speechError.value=message(e,'语音生成失败，请重试');speechLoading.value=false}}
}
async function poll(task,current,deadline){
  if(current!==epoch)return;
  if(task.status==='COMPLETED'){audioUrl.value=env.baseHttps+task.audioUrl;speechLoading.value=false;return}
  if(task.status==='FAILED'){speechError.value=task.error||'语音生成失败，请重试';speechLoading.value=false;return}
  if(Date.now()>deadline){speechError.value='语音等待超时，可点击获取语音查询原任务';speechLoading.value=false;return}
  speechStatus.value=task.status==='PENDING'?'语音排队中…':'正在合成语音…';
  pollTimer=setTimeout(async()=>{try{const r=await reqGetAudioTask(task.taskId);await poll(r.data,current,deadline)}catch(e){if(current===epoch){speechError.value=message(e,'语音状态查询失败，请重试');speechLoading.value=false}}},1200);
}
function play(){
  if(playing.value){audio.pause();playing.value=false;paused.value=true;return}
  if(!audioUrl.value)return;
  if(!audio){audio=uni.createInnerAudioContext();audio.src=audioUrl.value;audio.onPlay(()=>{playing.value=true;paused.value=false});audio.onEnded(()=>{playing.value=false;paused.value=false});audio.onError(()=>{speechError.value='音频播放失败，请重新获取语音';stopAudio()})}
  audio.play();
}
function cleanup(){epoch++;loading.value=false;cancelPolling();stopAudio();stopQuestion()}
onMounted(load);onShow(async()=>{try{capabilities.value=(await reqGetCapabilities()).data}catch(e){}});onHide(cleanup);onBeforeUnmount(cleanup);
</script>

<style scoped>
.page{min-height:100vh;background:#f5f2eb;color:#263d36;padding:48rpx 36rpx 80rpx;box-sizing:border-box}.eyebrow{font-size:23rpx;letter-spacing:3rpx;color:#607468}.title{font-size:48rpx;font-weight:650;margin:16rpx 0}.subtitle,.muted{color:#778078;font-size:25rpx;line-height:1.7}.products{display:flex;flex-wrap:wrap;gap:24rpx;margin:38rpx 0}.product-card{flex:1;min-width:240rpx;padding:28rpx;border:2rpx solid #deded3;border-radius:18rpx;background:#fcfaf5;cursor:pointer}.product-card.active{border-color:#416958;background:#eaf0e5}.number{font-size:20rpx;color:#718170;letter-spacing:2rpx}.product-name{font-size:31rpx;font-weight:600;margin:20rpx 0 12rpx}.price{font-size:32rpx;margin-top:20rpx}.price text{font-size:20rpx;margin-left:8rpx}.columns{display:flex;gap:28rpx;align-items:flex-start}.panel{padding:32rpx;border-radius:20rpx;background:#fffdf8;border:1rpx solid #e6e3d8;box-sizing:border-box}.facts{width:36%}.workbench{flex:1;min-width:0}.section-title{font-size:30rpx;font-weight:650;margin-bottom:20rpx}.spaced{margin-top:32rpx}.field{font-size:25rpx;line-height:1.8;margin-bottom:20rpx}.label{display:block;color:#819084;font-size:22rpx}.document{padding:20rpx 0;border-top:1rpx solid #ebe9df;font-size:25rpx;cursor:pointer}.document .muted{float:right;font-size:22rpx}.document-text{display:block;white-space:pre-wrap;font-size:24rpx;line-height:1.9;margin-top:16rpx}.disclaimer{font-size:21rpx;line-height:1.8;color:#8c8c7a;margin-top:32rpx}.notice{padding:20rpx 24rpx;background:#f6f0df;border-radius:12rpx;font-size:25rpx;line-height:1.7;margin:16rpx 0}.error{background:#faebe6;color:#9a4632}.options{display:flex;gap:12rpx;flex-wrap:wrap;margin:24rpx 0}.option{border:1rpx solid #dedfd4;border-radius:8rpx;padding:14rpx;font-size:24rpx}.primary{background:#345747!important;color:white!important;font-size:27rpx}.history{font-size:24rpx;color:#607565;margin:28rpx 0 20rpx}.badge{color:#837449;font-size:21rpx;margin:16rpx 0}.script{white-space:pre-wrap;font-size:29rpx;line-height:2}.sources{font-size:22rpx;color:#839080;margin-top:24rpx}.qa{border-top:1rpx solid #e6e3d8;margin-top:36rpx;padding-top:28rpx}.question-input{width:100%;box-sizing:border-box;background:#faf9f3;border:1rpx solid #d9ded3;border-radius:12rpx;padding:20rpx;margin-top:20rpx;min-height:120rpx;font-size:26rpx}.ask-button{margin:16rpx 0;background:#345747!important;color:white!important;font-size:26rpx}.answer{background:#f0f3eb;border-radius:12rpx;padding:4rpx 24rpx 20rpx}.speech{border-top:1rpx solid #e6e3d8;margin-top:36rpx;padding-top:28rpx}.audio-actions{display:flex;flex-wrap:wrap;gap:12rpx;margin:20rpx 0}.audio-actions button{font-size:24rpx;margin:0;background:#edf1e9;color:#345747}@media(min-width:1100px){.page{padding-left:calc((100vw - 1040px)/2);padding-right:calc((100vw - 1040px)/2)}}@media(max-width:700px){.columns{display:block}.facts{width:100%;margin-bottom:24rpx}.product-card{min-width:100%;box-sizing:border-box}.title{font-size:40rpx}}
</style>
