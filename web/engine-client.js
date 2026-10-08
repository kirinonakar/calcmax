const EXECUTION_TIMEOUT_MS = 60000;
const EXECUTION_TIMEOUT_ERROR = '계산 시간이 60초를 초과했습니다.';

export class EngineClient extends EventTarget {
  constructor() { super(); this.counter = 0; this.pending = null; this.start(); }
  emit(type,detail) { this.dispatchEvent(new CustomEvent(type,{detail})); }
  start(attempt=0) {
    clearTimeout(this.startupTimer);
    this.ready = false;
    const worker = this.worker = new Worker(new URL('./worker.js',import.meta.url),{type:'module'});
    const fail = error => {
      if (this.worker !== worker) return;
      clearTimeout(this.startupTimer);
      const initializing = !this.ready;
      worker.terminate(); this.worker = null; this.ready = false;
      this.finish({ok:false,error});
      if (initializing && attempt < 1) {
        this.emit('status','계산 엔진 로딩을 다시 시도합니다…');
        this.start(attempt+1);
      } else this.emit('status',error);
    };
    worker.onmessage = ({data}) => {
      // A terminated startup attempt can still have queued messages.
      if (this.worker !== worker) return;
      if (data.type === 'ready') { clearTimeout(this.startupTimer); this.ready = true; this.emit('status','계산 엔진 준비 완료 · WASM'); this.emit('ready'); }
      else if (data.type === 'status') this.emit('status',data.message);
      else if (data.type === 'fatal') fail(data.error);
      else if (data.type === 'input' && data.id === this.pending?.id) this.requestInput(data);
      else if (data.type === 'result' && data.id === this.pending?.id) this.finish(data.result);
    };
    worker.onerror = event => fail(event.message || '엔진을 시작할 수 없습니다. 정적 서버와 빌드 파일을 확인해 주세요.');
    // Cold WASM downloads may be slow, but must never leave the UI loading forever.
    this.startupTimer = setTimeout(() => fail('계산 엔진 로딩 시간이 초과되었습니다. 다시 로딩을 눌러 주세요.'),120000);
  }
  finish(result) {
    if (!this.pending) return;
    clearTimeout(this.pending.timer);
    this.pending.inputController?.abort();
    const {resolve,background} = this.pending; this.pending = null;
    this.emit('activity',false);
    if (!background) this.emit('busy',false);
    resolve(result);
  }
  async requestInput(data) {
    const pending=this.pending,worker=this.worker;
    if(pending.waitingInput)return;
    clearTimeout(pending.timer);
    pending.remaining=Math.max(0,pending.remaining-(Date.now()-pending.started));
    pending.waitingInput=true;
    const controller=pending.inputController=new AbortController();
    try {
      const value=await pending.onInput?.({...data,signal:controller.signal});
      if(this.pending!==pending||this.worker!==worker)return;
      if(value===null||value===undefined){this.cancel();return;}
      pending.waitingInput=false;
      pending.inputController=null;
      pending.started=Date.now();
      pending.timer=setTimeout(()=>this.cancel(EXECUTION_TIMEOUT_ERROR),pending.remaining);
      worker.postMessage({type:'input',id:data.id,inputId:data.inputId,value:String(value)});
    } catch(error) {
      if(this.pending===pending&&this.worker===worker)this.cancel(String(error));
    }
  }
  execute(request,{background=false,onInput,context=''}={}) {
    if (!this.ready) return Promise.resolve({ok:false,error:'계산 엔진이 로딩 중입니다.'});
    if (this.pending?.background && !background) {
      // Explicit work takes priority, while allowing the current preview to finish.
      this.pending.background = false;
      this.emit('busy',true);
      const worker=this.worker;
      return this.pending.promise.then(result => this.worker===worker&&this.ready ? this.execute(request,{onInput,context}) : result);
    }
    if (this.pending) return Promise.resolve({ok:false,error:'계산 중입니다. 중지한 뒤 다시 실행해 주세요.'});
    let resolve;
    const promise = new Promise(done => { resolve = done; });
    const id = ++this.counter;
    this.pending = {id,resolve,promise,background,onInput,context,remaining:EXECUTION_TIMEOUT_MS,started:Date.now(),timer:setTimeout(() => this.cancel(EXECUTION_TIMEOUT_ERROR),EXECUTION_TIMEOUT_MS)};
    this.emit('activity',true);
    if (!background) this.emit('busy',true);
    this.worker.postMessage({id,request});
    return promise;
  }
  cancel(message='계산이 중지되었습니다.') {
    clearTimeout(this.startupTimer);
    this.worker?.terminate();
    this.worker = null; this.ready = false;
    this.finish({ok:false,error:message});
    this.emit('status','계산 엔진 재시작…');
    this.start();
  }
}
