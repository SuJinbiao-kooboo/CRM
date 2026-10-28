const fs = require('fs');
const cheerio = require('cheerio');
const items = JSON.parse(fs.readFileSync('base_items.json','utf8'));
const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36';

function cleanText(html){
  const $ = cheerio.load(html);
  $('script,style,noscript,svg,iframe').remove();
  return $('body').text().replace(/\s+/g,' ').trim();
}
function extractEmails(text){
  const set = new Set();
  for (const m of text.matchAll(/[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}/g)) {
    const e = m[0].toLowerCase().replace(/[.,;:]+$/,'');
    if (/\.(png|jpg|jpeg|gif|svg|webp|css|js)$/i.test(e)) continue;
    if (/@(example|sentry|wixpress|2x|3x)\./i.test(e)) continue;
    set.add(e);
  }
  return [...set];
}
function extractPhones(text){
  const set = new Set();
  const re = /(?:\+|00)\d{1,3}[\s.\-]?(?:\(?\d{1,4}\)?[\s.\-]?){2,6}\d{2,4}/g;
  for (const m of text.matchAll(re)) {
    const p = m[0].trim();
    const digits = p.replace(/\D/g,'');
    if (digits.length < 8 || digits.length > 18) continue;
    set.add(p);
  }
  return [...set].slice(0,10);
}
async function get(url){
  const c = new AbortController(); const t = setTimeout(()=>c.abort(), 15000);
  try {
    const r = await fetch(url, {signal:c.signal, redirect:'follow', headers:{'user-agent':UA,'accept':'text/html,application/xhtml+xml','accept-language':'en-US,en;q=0.9'}});
    const ct = r.headers.get('content-type')||'';
    if (!ct.includes('html')) return {status:r.status, finalUrl:r.url, html:'', contentType:ct};
    const html = await r.text();
    return {status:r.status, finalUrl:r.url, html, contentType:ct};
  } finally { clearTimeout(t); }
}
async function processOne(it){
  const out = {id:it.exhibitorID, name:it.name, url:it.url, pages:[], emails:[], phones:[], error:null};
  let html='', base='';
  try { const r = await get(it.url); out.homeStatus=r.status; out.finalUrl=r.finalUrl; html=r.html; base=r.finalUrl||it.url; }
  catch(e){ out.error='home:'+e.message; }
  if (html) {
    const $ = cheerio.load(html);
    out.title = $('title').text().trim().slice(0,200);
    out.metaDesc = ($('meta[name="description"]').attr('content')||'').slice(0,300);
    const text = cleanText(html);
    out.pages.push({url:base, text:text.slice(0,60000)});
    out.emails.push(...extractEmails(html));
    out.phones.push(...extractPhones(text));
    const links=[];
    $('a[href]').each((i,a)=>{ const href=$(a).attr('href'); const t=$(a).text().trim().toLowerCase(); if(!href) return; try{ const u=new URL(href,base); if(u.hostname.replace(/^www\./,'')!==new URL(base).hostname.replace(/^www\./,'')) return; if(/contact|about|company|impressum|imprint|legal|location|support|corporate/i.test(u.pathname+' '+t)) links.push(u.href); }catch(e){} });
    out.contactLinks=[...new Set(links)].slice(0,6);
  }
  for (const link of (out.contactLinks||[]).slice(0,2)) {
    try {
      const r = await get(link);
      if (r.html) { const text=cleanText(r.html); out.pages.push({url:r.finalUrl, text:text.slice(0,60000)}); out.emails.push(...extractEmails(r.html)); out.phones.push(...extractPhones(text)); }
    } catch(e){}
  }
  out.emails=[...new Set(out.emails)].slice(0,10);
  out.phones=[...new Set(out.phones)].slice(0,10);
  out.combinedText = out.pages.map(p=>p.text).join('\n').slice(0,120000);
  delete out.pages;
  delete out.contactLinks;
  return out;
}
(async()=>{
  const results=[]; let done=0;
  const queue=[...items];
  async function worker(){
    while(queue.length){
      const it=queue.shift(); let r;
      try{ r=await processOne(it); }catch(e){ r={id:it.exhibitorID,name:it.name,url:it.url,error:e.message,emails:[],phones:[]}; }
      results.push(r); done++;
      if(done%25===0) console.log(done, '/', items.length, 'emails', results.filter(x=>x.emails?.length).length, 'phones', results.filter(x=>x.phones?.length).length);
    }
  }
  await Promise.all(Array.from({length:20},worker));
  fs.writeFileSync('site_data.json', JSON.stringify(results,null,1));
  console.log('DONE', results.length, 'email coverage', results.filter(x=>x.emails?.length).length, 'phone coverage', results.filter(x=>x.phones?.length).length, 'text coverage', results.filter(x=>x.combinedText?.length>500).length);
})();
