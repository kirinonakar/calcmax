const plain=text=>text.replace(/\*\*|`/g,'');
export function helpExampleInput(example){return plain(example.split('→')[0]).trim();}
export function parseCatalogHelp(markdown,query=''){
  const blocks=[];let category='';
  for(const line of markdown.split(/\r?\n/)){
    if(!line.trim())continue;
    if(line.startsWith('## ')){category=plain(line.slice(3));blocks.push({kind:'category',text:category});}
    else if(line.startsWith('# '))blocks.push({kind:'heading',text:plain(line.slice(2))});
    else {
      const entry=line.match(/^`([^`]+)`\s*[—–-]\s*(.*)$/);
      if(entry)blocks.push({kind:'entry',signature:entry[1],text:plain(entry[2]),category});
      else if(/^(Example:|예(?:시|제):)/.test(line)&&blocks.at(-1)?.kind==='entry')blocks.at(-1).example=plain(line.replace(/^[^:]+:\s*/,''));
      else blocks.push({kind:'body',text:plain(line),category});
    }
  }
  const needle=query.trim().toLowerCase();
  if(!needle)return blocks;
  return blocks.filter(block=>!['category','heading'].includes(block.kind)&&`${block.signature||''} ${block.text} ${block.example||''}`.toLowerCase().includes(needle));
}
