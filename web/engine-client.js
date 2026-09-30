export class EngineClient extends EventTarget {
  constructor() { super(); this.counter = 0; this.pending = null; this.start(); }
  emit(type,detail) { this.dispatchEvent(new CustomEvent(type,{detail})); }
  start() {
    this.ready = false;
    this.worker = new Worker(new URL('./worker.js',import.meta.url),{type:'module'});
    this.worker.onmessage = ({data}) => {
      if (data.type === 'ready') { this.ready = true; this.emit('status','계산 엔진 준비 완료 · WASM'); this.emit('ready'); }
      else if (data.type === 'status') this.emit('status',data.message);
      else if (data.type === 'fatal') { this.ready = false; this.emit('status',data.error); this.finish({ok:false,error:data.error}); }
      else if (data.type === 'result' && data.id === this.pending?.id) this.finish(data.result);
    };
    this.worker.onerror = event => { this.ready = false; this.emit('status',event.message || '엔진을 시작할 수 없습니다. 정적 서버와 빌드 파일을 확인해 주세요.'); this.finish({ok:false,error:event.message || 'Engine failed'}); };
  }
  finish(result) {
    if (!this.pending) return;
    clearTimeout(this.pending.timer);
    const {resolve} = this.pending; this.pending = null;
    this.emit('busy',false); resolve(result);
  }
  execute(request) {
    if (!this.ready) return Promise.resolve({ok:false,error:'계산 엔진이 로딩 중입니다.'});
    if (this.pending) return Promise.resolve({ok:false,error:'계산 중입니다. 중지한 뒤 다시 실행해 주세요.'});
    return new Promise(resolve => {
      const id = ++this.counter;
      this.pending = {id,resolve,timer:setTimeout(() => this.cancel('계산 시간이 20초를 초과했습니다.'),20000)};
      this.emit('busy',true);
      this.worker.postMessage({id,request});
    });
  }
  cancel(message='계산이 중지되었습니다.') {
    this.worker.terminate();
    this.finish({ok:false,error:message});
    this.emit('status','계산 엔진 재시작…');
    this.start();
  }
}
