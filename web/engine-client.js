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
      else if (data.type === 'result' && data.id === this.pending?.id) this.finish(data.result);
    };
    worker.onerror = event => fail(event.message || '엔진을 시작할 수 없습니다. 정적 서버와 빌드 파일을 확인해 주세요.');
    // Cold WASM downloads may be slow, but must never leave the UI loading forever.
    this.startupTimer = setTimeout(() => fail('계산 엔진 로딩 시간이 초과되었습니다. 다시 로딩을 눌러 주세요.'),120000);
  }
  finish(result) {
    if (!this.pending) return;
    clearTimeout(this.pending.timer);
    const {resolve,background} = this.pending; this.pending = null;
    this.emit('activity',false);
    if (!background) this.emit('busy',false);
    resolve(result);
  }
  execute(request,{background=false}={}) {
    if (!this.ready) return Promise.resolve({ok:false,error:'계산 엔진이 로딩 중입니다.'});
    if (this.pending?.background && !background) {
      // Explicit work takes priority, while allowing the current preview to finish.
      this.pending.background = false;
      this.emit('busy',true);
      return this.pending.promise.then(() => this.execute(request));
    }
    if (this.pending) return Promise.resolve({ok:false,error:'계산 중입니다. 중지한 뒤 다시 실행해 주세요.'});
    let resolve;
    const promise = new Promise(done => { resolve = done; });
    const id = ++this.counter;
    this.pending = {id,resolve,promise,background,timer:setTimeout(() => this.cancel('계산 시간이 20초를 초과했습니다.'),20000)};
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
