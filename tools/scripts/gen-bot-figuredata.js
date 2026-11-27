#!/usr/bin/env node
// Regenerates Kepler-Server/src/main/resources/bots/bot_figuredata.txt (used by game/bot/BotFigure.java).
//
// Inputs:  production gamedata/figuredata.txt and the extracted hh_people_* casts of kepler-web-client.
// Usage:   curl -s https://www.habbop.online/gamedata/figuredata.txt -o /tmp/figuredata.txt
//          node tools/scripts/gen-bot-figuredata.js /tmp/figuredata.txt \
//               ../kepler-web-client/packages/assets-pipeline/.cache/extract/ > Kepler-Server/src/main/resources/bots/bot_figuredata.txt
// Needs `sharp` (resolved from the kepler-web-client node_modules: NODE_PATH=../kepler-web-client/node_modules).
//
// "alias" = for a legacy (bot) model without a usable v14 set of that sex, the usable model whose
// h_std_<part>_<model>_<dir>_0 bitmap overlaps most (IoU of the opaque pixels, aligned on the reg point,
// averaged over directions 0,1,2,3,7).
const fs = require('fs');
const sharp = require('sharp');
const [figuredataPath, EX] = process.argv.slice(2);
if (!figuredataPath || !EX) { console.error('usage: gen-bot-figuredata.js <figuredata.txt> <extract dir>/'); process.exit(1); }
function parse(s){let i=0;
 function ws(){while(/\s/.test(s[i]))i++;}
 function val(){ws();const c=s[i];
  if(c==='['){i++;ws();
    // proplist if next is "key": or #sym:
    let save=i;let isProp=false;
    if(s[i]===']'){i++;return [];}
    if(s[i]===':'&&s[i+1]===']'){i+=2;return {};}
    let v1=val();ws();
    if(s[i]===':'){isProp=true;}
    if(isProp){const o={};i++;o[v1]=val();ws();
      while(s[i]===','){i++;const k=val();ws();if(s[i]!==':')throw Error('exp : at '+i);i++;o[k]=val();ws();}
      if(s[i]!==']')throw Error('exp ] at '+i+' '+s.slice(i,i+20));i++;return o;}
    const a=[v1];while(s[i]===','){i++;a.push(val());ws();}
    if(s[i]!==']')throw Error('exp ] at '+i);i++;return a;}
  if(c==='"'){let j=s.indexOf('"',i+1);const r=s.slice(i+1,j);i=j+1;return r;}
  let m=/^[-0-9.]+/.exec(s.slice(i));if(m){i+=m[0].length;return Number(m[0]);}
  m=/^#?[A-Za-z_]\w*/.exec(s.slice(i));if(m){i+=m[0].length;return m[0];}
  throw Error('bad at '+i+' '+s.slice(i,i+20));}
 return val();}
const fd = parse(fs.readFileSync(figuredataPath, 'latin1'));
// index h-size bitmaps
const bm={};
for(const c of ['hh_people_1','hh_people_2','hh_human']){const f=EX+c+'/'+c+'/bitmaps_meta.tsv';if(!fs.existsSync(f))continue;
 for(const l of fs.readFileSync(f,'utf8').split('\n').slice(1)){const t=l.split('\t');if(t.length<13)continue;
  const name=t[1].replace(/^\d+_/,'');const m=/^h_std_([a-z]{2})_(\d+)_(\d)_0$/.exec(name);if(!m)continue;
  bm[`${m[1]}_${+m[2]}_${m[3]}`]={file:EX+c+'/'+c+'/'+t[12],w:+t[2],h:+t[3],rx:+t[4],ry:+t[5],depth:+t[10]};}}
const cache={};
async function mask(part,model,dir){const k=`${part}_${model}_${dir}`;if(k in cache)return cache[k];const b=bm[k];if(!b){cache[k]=null;return null;}
 const {data,info}=await sharp(b.file).ensureAlpha().raw().toBuffer({resolveWithObject:true});const pts=new Set();
 for(let y=0;y<info.height;y++)for(let x=0;x<info.width;x++){const i=(y*info.width+x)*4;
   const on=b.depth===32? (data[i+3]>0|| (data[i]+data[i+1]+data[i+2])<750) : data[i+3]>0;
   if(on)pts.add((x+b.rx+200)*1000+(y+b.ry+200));}
 return cache[k]=pts;}
async function sim(part,a,b){let tot=0,n=0;for(const d of [0,1,2,3,7]){const A=await mask(part,a,d),B=await mask(part,b,d);if(!A||!B)continue;
 let inter=0;for(const p of A)if(B.has(p))inter++;tot+=inter/(A.size+B.size-inter||1);n++;}return n?tot/n:-1;}
// 806-809 / 822-823: ch 200/201/202 are 32-bit with an empty alpha in hh_people_small_2 (invisible shirt).
// 176 / 580: hr 457 is 32-bit in normal size. hd model 2 has no small (sh_std_hd_002) bitmap.
const EXCL=new Set([806,807,808,809,822,823,176,580]);
const ok=(sx,p,s)=>!EXCL.has(s.s)&&!(p==='hd'&&+s.p.hd!==1);
const lines=['# Generated from production gamedata/figuredata.txt and the hh_people_* casts (tools/scripts/gen-bot-figuredata.js).',
'# set <sex> <part> <setId> <model> <sleeve|eyes|-> <ok|x> <colours>   (x = invisible/missing in small rooms)',
'# alias <sex> <part> <legacyModel> <v14Model>   (closest v14 model by bitmap overlap, h_std_*, dirs 0,1,2,3,7)'];
for(const sx of ['M','F'])for(const p of ['hr','hd','lg','sh','ch'])for(const s of fd[sx][p]){
  const extra=p==='ch'?(s.p.ls||'-'):p==='hd'?(s.p.ey||'-'):'-';
  lines.push(['set',sx,p,s.s,+s.p[p],extra==='-'?'-':+extra,ok(sx,p,s)?'ok':'x',s.c.join(',')].join(' '));}
(async()=>{
 const legacy={};for(const k of Object.keys(bm)){const [p,m]=k.split('_');if(['hr','ch','lg','sh'].includes(p))(legacy[p]=legacy[p]||new Set()).add(+m);}
 for(const sx of ['M','F'])for(const p of ['hr','ch','lg','sh']){
  const avail=[...new Set(fd[sx][p].filter(s=>ok(sx,p,s)).map(s=>+s.p[p]))];
  const all=new Set([...legacy[p],...fd.M[p].map(s=>+s.p[p]),...fd.F[p].map(s=>+s.p[p])]);
  for(const m of [...all].sort((a,b)=>a-b)){if(avail.includes(m))continue;
   let best=null,bs=-1;for(const a of avail){const s=await sim(p,m,a);if(s>bs){bs=s;best=a;}}
   if(best!==null&&bs>0)lines.push(`alias ${sx} ${p} ${m} ${best}`);else console.error('no alias',sx,p,m);}}
 process.stdout.write(lines.join('\n')+'\n');
})();
