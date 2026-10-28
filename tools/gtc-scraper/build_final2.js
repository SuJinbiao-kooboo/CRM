const fs=require('fs');
const items=JSON.parse(fs.readFileSync('C:/Project/RuoYi-Vue/tools/gtc-scraper/base_items.json','utf8'));
const sites=JSON.parse(fs.readFileSync('C:/Project/RuoYi-Vue/tools/gtc-scraper/site_data.json','utf8'));
const contacts=JSON.parse(fs.readFileSync('C:/Project/RuoYi-Vue/tools/gtc-scraper/contact_data.json','utf8'));
const known=require('C:/Project/RuoYi-Vue/tools/gtc-scraper/known_hq.js');
const extra=JSON.parse(fs.readFileSync('C:/Project/RuoYi-Vue/tools/gtc-scraper/extra_hq.json','utf8'));
const cats=require('C:/Project/RuoYi-Vue/tools/gtc-scraper/cats.js');
const fixes={'ASROCK RACK INC.':'server','Adaption':'software','Check Point':'software','Flexcompute':'software','Jump Trading':'industry','Wayvw':'industry','CAMB.AI':'software','Photoroom':'software','Skymeric Technologies LLC':'services','Tripo':'software','KX':'software','Vention':'industry','ALLSIDES':'industry','Cohere Technologies':'network','Idealworks':'industry','Simplismart':'software','Axiomtek Co., Ltd.':'server'};
const norm=s=>s.toLowerCase().replace(/&/g,'and').replace(/[^a-z0-9]+/g,' ').replace(/\b(inc|llc|ltd|limited|corp|corporation|co|company|technologies|technology|systems|system|group|holdings|plc|gmbh|se|ag|sa|nv|bv|pte|the)\b/g,' ').replace(/\s+/g,' ').trim();
const hq=new Map(); for(const [k,v] of Object.entries({...known,...extra})) hq.set(norm(k),v);
const catMap=new Map(); for(const [c,arr] of Object.entries(cats)) for(const n of arr) catMap.set(norm(n),c); for(const [n,c] of Object.entries(fixes)) catMap.set(norm(n),c);
const catLabel={server:'服务器/整机/系统集成',gpu:'GPU/CPU/加速卡/AI芯片',semi:'半导体/芯片',storage:'存储/内存',network:'网络/互连/光模块',thermal:'散热/电源/机柜/数据中心基础设施',pcb:'PCB/连接器/电子制造',cloud:'云服务/算力租赁/数据中心',software:'AI软件/平台/工具',services:'咨询/集成/分销服务',quantum:'量子计算',industry:'行业AI应用',other:'其他'};
const infraSoft=new Set(['Canonical','Red Hat','SUSE','Mirantis','Spectro Cloud','ZEDEDA','vCluster','Docker','OpenNebula Systems','Nutanix','Tailscale','Rafay','Cloudflare','Hammerspace','VAST Data','Weka','MinIO','Quobyte','VDURA','Zadara, Inc.','Graid Technology','Liqid','Tensormesh','SpiralDB','SurrealDB','lakeFS','Cohesity'].map(norm));
const regionMap={'美国':'北美','加拿大':'北美','墨西哥':'北美','巴西':'拉美','乌拉圭':'拉美','阿根廷':'拉美','智利':'拉美','英国':'欧洲','爱尔兰':'欧洲','德国':'欧洲','法国':'欧洲','荷兰':'欧洲','比利时':'欧洲','卢森堡':'欧洲','瑞士':'欧洲','奥地利':'欧洲','意大利':'欧洲','西班牙':'欧洲','葡萄牙':'欧洲','瑞典':'欧洲','挪威':'欧洲','芬兰':'欧洲','波兰':'欧洲','捷克':'欧洲','罗马尼亚':'欧洲','乌克兰':'欧洲','土耳其':'中东','以色列':'中东','阿联酋':'中东','沙特阿拉伯':'中东','卡塔尔':'中东','科威特':'中东','南非':'非洲','印度':'南亚','中国大陆':'东亚','中国台湾':'东亚','中国香港':'东亚','日本':'东亚','韩国':'东亚','新加坡':'东南亚','马来西亚':'东南亚','泰国':'东南亚','越南':'东南亚','印度尼西亚':'东南亚','菲律宾':'东南亚','澳大利亚':'大洋洲','新西兰':'大洋洲'};
const regionOf=c=>regionMap[c]||(c==='待核实'?'待核实':'其他');
const urlFix={'aka.ms':'https://azure.microsoft.com','product.skhynix.com':'https://www.skhynix.com','ebg.inventec.com':'https://www.inventec.com','en.megmeet.com':'https://www.megmeet.com','www.sw.siemens.com':'https://www.siemens.com','sap.com':'https://www.sap.com'};
const nameUrl={'Lucid':'https://lucidmotors.com/','Meta':'https://www.meta.com/','FPT':'https://fpt.com/','ASUS':'https://www.asus.com/','PEGATRON':'https://www.pegatroncorp.com/','Hexagon Robotics':'https://hexagon.com/','AVerMedia Technologies, Inc.':'https://www.avermedia.com/','Lightricks':'https://www.lightricks.com/','Geely Auto Group':'https://www.geely.com/','San Francisco Compute Company':'https://www.sfcompute.com/'};
function officialUrl(u,name){if(nameUrl[name])return nameUrl[name];try{const x=new URL(u);if(urlFix[x.hostname])return urlFix[x.hostname];return x.origin+'/';}catch(e){return u||'';}}
function cleanEmail(e){e=e.toLowerCase().replace(/^[^a-z0-9]+/,'').replace(/[.,;:]+$/,'');if(/example|sentry|wixpress|you@|user@|name@|email@|test@|johnsmith|u003e|@company\.|@firm\.|@lab\.|hospital\.nhs|@shareholders-online|immixgroup|apcoworldwide|lewispr|hughes-agency|miraisemi|\.png|\.jpg|\.svg|\.css|\.js$/.test(e))return null;if(!/^[a-z0-9._%+\-]+@[a-z0-9.\-]+\.[a-z]{2,}$/.test(e))return null;return e;}
function pickEmails(list,host,name){const dom=host.replace(/^www\./,'').split('.').slice(-2).join('.');const normName=(name||'').toLowerCase().replace(/[^a-z0-9]+/g,' ').split(' ').filter(w=>w.length>=4&&!['inc','llc','ltd','corp','company','technologies','technology','systems','system','group','solutions','data','cloud','global','digital','industries','international','america','the'].includes(w)).sort((a,b)=>b.length-a.length)[0]||'';const es=[...new Set((list||[]).map(cleanEmail).filter(Boolean))].filter(e=>!/^\d/.test(e));const match=es.filter(e=>{const d=e.split('@')[1];return d===dom||d.endsWith('.'+dom)||(normName&&d.includes(normName));});const roleOrder=['info','sales','contact','hello','support','business','bd','marketing','enquiry','inquiry','press','pr','media','ir','privacy','security','legal','webmaster'];const roleScore=e=>{const l=e.split('@')[0];for(let i=0;i<roleOrder.length;i++)if(l===roleOrder[i]||l.startsWith(roleOrder[i]+'.')||l.startsWith(roleOrder[i]+'-'))return i;return 99;};return [...new Set(match)].filter(e=>roleScore(e)<99).sort((a,b)=>roleScore(a)-roleScore(b)).slice(0,2);}
function scorePhone(p){const d=p.replace(/\D/g,'');if(d.length<9||d.length>14)return -1;let s=0;if(/^\+\d{1,3}[\s.\-]/.test(p))s+=6;else if(/^\+/.test(p))s+=4;if(/^\(\d{3}\)\s*\d{3}-\d{4}$/.test(p))s+=5;if(/^1-\d{3}-\d{3}-\d{4}$/.test(p))s+=5;if(/^\d{3}-\d{3}-\d{4}$/.test(p))s+=3;if(/^\d{3}\.\d{3}\.\d{4}$/.test(p))s+=2;if(d.length===10||d.length===11)s+=2;if(/\./.test(p))s-=2;if(/^(19|20)\d{2}/.test(d)&&d.length<=10)s-=4;if(/0{4,}/.test(d))s-=4;if(/(\d)\1{4,}/.test(d))s-=4;if(/^00(800|8)/.test(d))s-=2;return s;}
function pickPhones(list){const arr=(list||[]).map(p=>({p:p.trim(),s:scorePhone(p.trim())})).filter(x=>x.s>=3).sort((a,b)=>b.s-a.s);const out=[];for(const x of arr){if(out.length>=2)break;if(!out.includes(x.p))out.push(x.p);}return out;}
function firstSentence(s){s=(s||'').replace(/\s+/g,' ').trim();if(!s)return'';const m=s.match(/^(.{20,240}?[.!?])\s/);return (m?m[1]:s).slice(0,240);}
const rows=items.map(it=>{
 const site=sites.find(x=>x.id===it.exhibitorID)||{}; const contact=contacts.find(x=>x.id===it.exhibitorID)||{}; let host='';try{host=new URL(it.url).hostname}catch(e){}
 const country=hq.get(norm(it.name))||'待核实'; const cat=catMap.get(norm(it.name))||'other';
 let rel,relDesc;
 if(['server','gpu','semi','storage','network','thermal','pcb'].includes(cat)){rel='是';relDesc={server:'核心：服务器/整机/系统集成',gpu:'核心：GPU/CPU/加速卡/AI芯片',semi:'核心：半导体/芯片',storage:'核心：存储/内存',network:'核心：网络/互连/光模块',thermal:'核心：散热/电源/机柜/数据中心基础设施',pcb:'核心：PCB/连接器/电子制造'}[cat];}
 else if(cat==='quantum'){rel='是';relDesc='相关：量子计算硬件';}
 else if(cat==='cloud'){rel='是';relDesc='相关：云/算力租赁/数据中心';}
 else if(cat==='software'){if(infraSoft.has(norm(it.name))){rel='是';relDesc='相关：服务器/云基础软件';}else{rel='否';relDesc='软件/应用平台（非硬件）';}}
 else if(cat==='services'){rel='否';relDesc='咨询/集成/分销服务';}
 else if(cat==='industry'){rel='否';relDesc='行业AI应用/机器人/垂直场景';}
 else {rel='待核实';relDesc='业务信息不足，需人工核实';}
 const emails=pickEmails([...(site.emails||[]),...(contact.emails||[])],host,it.name); const phones=pickPhones([...(site.phones||[]),...(contact.phones||[])]); const contactNote=emails.length&&phones.length?'':emails.length?'官网未公开电话，建议使用邮箱或联系表单':phones.length?'官网未公开邮箱，建议电话联系':'官网未公开邮箱/电话，建议使用官网联系表单';
 return {
  '展商名称':it.name.trim(),'赞助级别':(it.attributevalues||[]).find(a=>a.attribute_id==='ExhibitorType')?.value||'','所在国家/区域':country,'区域':regionOf(country),
  '主营业务分类':catLabel[cat],'主营业务简介':firstSentence(it.description),'是否IT配件或服务器相关':rel,'相关度说明':relDesc,
  '官网':officialUrl(it.url,it.name.trim()),'联系邮箱':emails.join('; '),'联系电话':phones.join('; '),'联系方式备注':contactNote,
  '展位号':(it.booths||[]).map(b=>b.booth).join(' / '),'官方简介':(it.description||'').replace(/\s+/g,' ').trim(),'展商ID':it.exhibitorID,
  '数据来源':'NVIDIA GTC 2026 官网展商目录 + 企业官网公开信息'
 };
});
fs.writeFileSync('C:/Project/RuoYi-Vue/tools/gtc-scraper/final_rows.json',JSON.stringify(rows,null,1));
const stat=k=>{const m={};rows.forEach(r=>m[r[k]]=(m[r[k]]||0)+1);return m;};
console.log('rows',rows.length);console.log('country',JSON.stringify(stat('所在国家/区域')));console.log('cat',JSON.stringify(stat('主营业务分类')));console.log('rel',JSON.stringify(stat('是否IT配件或服务器相关')));
console.log('email',rows.filter(r=>r['联系邮箱']).length,'phone',rows.filter(r=>r['联系电话']).length);
console.log(rows.filter(r=>r['联系电话']).slice(0,10).map(r=>r['展商名称']+' | '+r['联系电话']).join('\n'));
