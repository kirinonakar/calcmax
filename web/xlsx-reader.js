import {computationLimitsRemoved} from './computation-limits.js';
const MAX_UNCOMPRESSED_BYTES=64*1024*1024;

function bitReader(bytes) {
  let position=0;
  return {
    read(count) {
      let value=0;
      for(let bit=0;bit<count;bit++) {
        if(position>=bytes.length*8)throw new Error('Invalid XLSX deflate stream');
        value|=((bytes[position>>>3]>>>(position&7))&1)<<bit;position++;
      }
      return value;
    },
    align(){position=(position+7)&~7;},
    readBytes(count) {
      this.align();const start=position>>>3,end=start+count;
      if(end>bytes.length)throw new Error('Invalid XLSX deflate stream');
      position=end*8;return bytes.subarray(start,end);
    }
  };
}

function huffman(lengths) {
  const max=Math.max(...lengths),counts=new Uint16Array(max+1),next=new Uint16Array(max+1),tables=Array.from({length:max+1},()=>new Map());
  for(const length of lengths)if(length)counts[length]++;
  let code=0;
  for(let length=1;length<=max;length++) {
    code=(code+(counts[length-1]||0))<<1;next[length]=code;
  }
  lengths.forEach((length,symbol)=>{if(length)tables[length].set(next[length]++,symbol);});
  return {max,tables};
}

function symbol(reader,tree) {
  let code=0;
  for(let length=1;length<=tree.max;length++) {
    code=(code<<1)|reader.read(1);const found=tree.tables[length].get(code);
    if(found!==undefined)return found;
  }
  throw new Error('Invalid XLSX Huffman code');
}

const LENGTH_BASE=[3,4,5,6,7,8,9,10,11,13,15,17,19,23,27,31,35,43,51,59,67,83,99,115,131,163,195,227,258];
const LENGTH_EXTRA=[0,0,0,0,0,0,0,0,1,1,1,1,2,2,2,2,3,3,3,3,4,4,4,4,5,5,5,5,0];
const DISTANCE_BASE=[1,2,3,4,5,7,9,13,17,25,33,49,65,97,129,193,257,385,513,769,1025,1537,2049,3073,4097,6145,8193,12289,16385,24577];
const DISTANCE_EXTRA=[0,0,0,0,1,1,2,2,3,3,4,4,5,5,6,6,7,7,8,8,9,9,10,10,11,11,12,12,13,13];

function fixedTrees() {
  return [huffman(Array.from({length:288},(_,i)=>i<=143?8:i<=255?9:i<=279?7:8)),huffman(Array(32).fill(5))];
}

function dynamicTrees(reader) {
  const literalCount=reader.read(5)+257,distanceCount=reader.read(5)+1,codeCount=reader.read(4)+4;
  const order=[16,17,18,0,8,7,9,6,10,5,11,4,12,3,13,2,14,1,15],codeLengths=Array(19).fill(0);
  for(let i=0;i<codeCount;i++)codeLengths[order[i]]=reader.read(3);
  const codeTree=huffman(codeLengths),lengths=[];
  while(lengths.length<literalCount+distanceCount) {
    const value=symbol(reader,codeTree);
    if(value<=15)lengths.push(value);
    else if(value===16) {
      if(!lengths.length)throw new Error('Invalid XLSX deflate repeat');
      const count=reader.read(2)+3,last=lengths.at(-1);
      for(let i=0;i<count;i++)lengths.push(last);
    } else {
      const count=value===17?reader.read(3)+3:reader.read(7)+11;
      for(let i=0;i<count;i++)lengths.push(0);
    }
    if(lengths.length>literalCount+distanceCount)throw new Error('Invalid XLSX deflate code lengths');
  }
  return [huffman(lengths.slice(0,literalCount)),huffman(lengths.slice(literalCount))];
}

function inflateRaw(compressed,expectedLength) {
  if(expectedLength>MAX_UNCOMPRESSED_BYTES)throw new Error('XLSX file is too large');
  const reader=bitReader(compressed),output=new Uint8Array(expectedLength);let cursor=0,final=false;
  const write=value=>{if(cursor>=output.length)throw new Error('Invalid XLSX deflate output');output[cursor++]=value;};
  while(!final) {
    final=reader.read(1)===1;const type=reader.read(2);
    if(type===0) {
      const block=reader.readBytes(4),length=block[0]|block[1]<<8,check=block[2]|block[3]<<8;
      if((length^0xffff)!==check)throw new Error('Invalid XLSX stored block');
      for(const value of reader.readBytes(length))write(value);
      continue;
    }
    if(type===3)throw new Error('Unsupported XLSX compression block');
    const [literalTree,distanceTree]=type===1?fixedTrees():dynamicTrees(reader);
    while(true) {
      const value=symbol(reader,literalTree);
      if(value<256){write(value);continue;}
      if(value===256)break;
      const lengthIndex=value-257;
      if(lengthIndex<0||lengthIndex>=LENGTH_BASE.length)throw new Error('Invalid XLSX match length');
      const length=LENGTH_BASE[lengthIndex]+reader.read(LENGTH_EXTRA[lengthIndex]),distanceCode=symbol(reader,distanceTree);
      if(distanceCode>=DISTANCE_BASE.length)throw new Error('Invalid XLSX match distance');
      const distance=DISTANCE_BASE[distanceCode]+reader.read(DISTANCE_EXTRA[distanceCode]);
      if(distance>cursor)throw new Error('Invalid XLSX match distance');
      for(let i=0;i<length;i++)write(output[cursor-distance]);
    }
  }
  if(cursor!==expectedLength)throw new Error('Invalid XLSX uncompressed size');
  return output;
}

async function unzipEntries(buffer) {
  if(buffer.byteLength>MAX_UNCOMPRESSED_BYTES)throw new Error('XLSX file is too large');
  const bytes=new Uint8Array(buffer),view=new DataView(buffer),minimum=Math.max(0,bytes.length-65_557);let end=-1;
  for(let i=bytes.length-22;i>=minimum;i--)if(view.getUint32(i,true)===0x06054b50){end=i;break;}
  if(end<0)throw new Error('This XLSX file has an invalid ZIP directory');
  const count=view.getUint16(end+10,true),directoryOffset=view.getUint32(end+16,true);
  if(count===0xffff||directoryOffset===0xffffffff)throw new Error('This XLSX file uses an unsupported ZIP64 archive');
  if(count>10000)throw new Error('This XLSX file contains too many ZIP entries');
  const files=new Map();let offset=directoryOffset,total=0;
  for(let index=0;index<count;index++) {
    if(view.getUint32(offset,true)!==0x02014b50)throw new Error('This XLSX file has a damaged ZIP directory');
    const flags=view.getUint16(offset+8,true),method=view.getUint16(offset+10,true),compressedSize=view.getUint32(offset+20,true),size=view.getUint32(offset+24,true);
    const nameLength=view.getUint16(offset+28,true),extraLength=view.getUint16(offset+30,true),commentLength=view.getUint16(offset+32,true),localOffset=view.getUint32(offset+42,true);
    const name=new TextDecoder().decode(bytes.subarray(offset+46,offset+46+nameLength));offset+=46+nameLength+extraLength+commentLength;
    if(name.endsWith('/'))continue;
    if(flags&1)throw new Error('Encrypted XLSX files are not supported');
    if(size>MAX_UNCOMPRESSED_BYTES||total+size>MAX_UNCOMPRESSED_BYTES)throw new Error('XLSX file is too large');
    if(view.getUint32(localOffset,true)!==0x04034b50)throw new Error('This XLSX file has a damaged ZIP entry');
    const dataOffset=localOffset+30+view.getUint16(localOffset+26,true)+view.getUint16(localOffset+28,true),compressed=bytes.subarray(dataOffset,dataOffset+compressedSize);
    if(dataOffset+compressedSize>bytes.length)throw new Error('This XLSX file has a truncated ZIP entry');
    let content;
    if(method===0)content=compressed.slice();
    else if(method===8) {
      try {
        if(typeof DecompressionStream!=='undefined') {
          const stream=new Blob([compressed]).stream().pipeThrough(new DecompressionStream('deflate-raw'));
          content=new Uint8Array(await new Response(stream).arrayBuffer());
          if(content.length!==size)throw new Error('Invalid XLSX uncompressed size');
        } else content=inflateRaw(compressed,size);
      } catch {content=inflateRaw(compressed,size);}
    } else throw new Error('This XLSX file uses an unsupported ZIP compression method');
    if(content.length!==size)throw new Error('Invalid XLSX uncompressed size');
    total+=size;files.set(name,content);
  }
  return files;
}

function xmlDocument(bytes,name) {
  if(!bytes)throw new Error(`XLSX is missing ${name}`);
  const xml=new DOMParser().parseFromString(new TextDecoder().decode(bytes),'application/xml');
  if(xml.getElementsByTagName('parsererror').length)throw new Error(`XLSX contains invalid ${name}`);
  return xml;
}

function elements(node,name) {return [...node.getElementsByTagNameNS('*',name)];}
function directElements(node,name) {return [...node.children].filter(child=>child.localName===name);}
function normalizePath(path) {
  const parts=[];
  for(const part of path.replace(/^\//,'').split('/'))if(part==='..')parts.pop();else if(part&&part!=='.')parts.push(part);
  return parts.join('/');
}

function worksheetList(files) {
  const workbook=xmlDocument(files.get('xl/workbook.xml'),'workbook.xml'),sheets=elements(workbook,'sheet');
  const relations=files.get('xl/_rels/workbook.xml.rels');
  const entries=relations?elements(xmlDocument(relations,'workbook.xml.rels'),'Relationship'):[];
  const targets=new Map(entries.filter(item=>(item.getAttribute('Type')||'').endsWith('/worksheet')).map(item=>[item.getAttribute('Id'),item.getAttribute('Target')]));
  const result=sheets.map((sheet,index)=>{
    const relationId=sheet.getAttributeNS('http://schemas.openxmlformats.org/officeDocument/2006/relationships','id')||sheet.getAttribute('r:id');
    const target=targets.get(relationId)||entries.find(item=>item.getAttribute('Id')===relationId)?.getAttribute('Target');
    return {name:sheet.getAttribute('name')||`Sheet ${index+1}`,path:target?normalizePath(target.startsWith('/')?target:`xl/${target}`):index===0?'xl/worksheets/sheet1.xml':''};
  }).filter(sheet=>sheet.path);
  if(!result.length) {
    const fallback='xl/worksheets/sheet1.xml';
    if(files.has(fallback))return [{name:'Sheet 1',path:fallback}];
  }
  return result;
}

function dateStyles(files) {
  const bytes=files.get('xl/styles.xml');if(!bytes)return [];
  const styles=xmlDocument(bytes,'styles.xml'),formats=new Map(elements(styles,'numFmt').map(item=>[Number(item.getAttribute('numFmtId')),item.getAttribute('formatCode')||'']));
  const customDate=format=>(format||'').replace(/"[^"]*"|\\.|_.|\*./g,'').replace(/\[[^\]]*\]/g,'').toLowerCase().match(/[yd]/);
  const builtIn=new Set([14,15,16,17,18,19,20,21,22,27,28,29,30,31,32,33,34,35,36,50,51,52,53,54,55,56,57,58]);
  const cellStyles=elements(styles,'cellXfs')[0];
  return cellStyles?directElements(cellStyles,'xf').map(item=>{const id=Number(item.getAttribute('numFmtId')||0);return builtIn.has(id)||customDate(formats.get(id));}):[];
}

function excelDate(serial) {
  if(!Number.isFinite(serial)||serial<1||serial>2_958_465)return null;
  const days=Math.floor(serial)-(serial>=60?1:0),date=new Date(Date.UTC(1899,11,31)+days*86_400_000);
  return Number.isNaN(date.getTime())?null:date.toISOString().slice(0,10);
}

function columnIndex(reference) {
  const letters=/^[A-Z]+/i.exec(reference||'')?.[0];if(!letters)return -1;
  let index=0;for(const letter of letters.toUpperCase())index=index*26+letter.charCodeAt(0)-64;
  return index-1;
}

function cellValue(cell,sharedStrings,dateStyle) {
  const type=cell.getAttribute('t')||'',style=Number(cell.getAttribute('s')||0);
  if(type==='inlineStr')return elements(cell,'t').map(item=>item.textContent||'').join('');
  const value=directElements(cell,'v')[0]?.textContent||'';
  if(type==='s')return sharedStrings[Number(value)]??'';
  if(type==='b')return value==='1'?'TRUE':'FALSE';
  if(dateStyle[style])return excelDate(Number(value))||value;
  return value;
}

function worksheetRows(sheetBytes,sharedStrings,styles) {
  const sheet=xmlDocument(sheetBytes,'worksheet'),rows=[];
  for(const row of elements(sheet,'row')) {
    const values=[];
    for(const cell of directElements(row,'c')) {
      const index=columnIndex(cell.getAttribute('r'));if(index<0)continue;
      if(index>=100)throw new Error('Use up to 100 XLSX data columns');
      values[index]=cellValue(cell,sharedStrings,styles);
    }
    if(values.some(value=>String(value??'').trim())) {
      if(!computationLimitsRemoved()&&rows.length>=5000)throw new Error('Use up to 5000 XLSX data rows');
      rows.push(Array.from({length:Math.max(1,values.length)},(_,index)=>String(values[index]??'').trim()));
    }
  }
  if(!rows.length)return [];
  const columns=Math.max(...rows.map(row=>row.length));
  if(columns>100)throw new Error('Use up to 100 XLSX data columns');
  return rows.map(row=>Array.from({length:columns},(_,index)=>row[index]||''));
}

/** Read all worksheets from a standard, unencrypted XLSX workbook. */
export async function readXlsxWorkbook(buffer) {
  const files=await unzipEntries(buffer),sheets=worksheetList(files);
  if(!sheets.length)throw new Error('XLSX workbook does not contain any worksheets');
  const sharedBytes=files.get('xl/sharedStrings.xml');
  const sharedStrings=sharedBytes?elements(xmlDocument(sharedBytes,'sharedStrings.xml'),'si').map(item=>elements(item,'t').map(text=>text.textContent||'').join('')):[];
  const styles=dateStyles(files);
  return sheets.map(({name,path})=>({name,rows:worksheetRows(files.get(path),sharedStrings,styles)}));
}
