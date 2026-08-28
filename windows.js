/* Free windows picker: select period, choose slots, copy/share/download ready text. */
(function(){
  const defs=['Вс','Пн','Вт','Ср','Чт','Пт','Сб'];
  function localDate(iso){return new Date(iso).toLocaleDateString('sv-SE',{timeZone:'Europe/Moscow'})}
  function localTime(iso){return new Date(iso).toLocaleTimeString('ru-RU',{timeZone:'Europe/Moscow',hour:'2-digit',minute:'2-digit'})}
  function dayName(ds){const d=new Date(ds+'T12:00:00+03:00');return defs[d.getDay()]}
  function prettyDate(ds){return new Date(ds+'T12:00:00+03:00').toLocaleDateString('ru-RU',{timeZone:'Europe/Moscow',day:'numeric',month:'long'})}
  function dateWithDay(ds){return `${prettyDate(ds)} (${dayName(ds)})`}
  function monthStart(ds){return ds.slice(0,7)+'-01'}
  function nextMonth(ds){const d=new Date(monthStart(ds)+'T12:00:00+03:00');d.setMonth(d.getMonth()+1);return d.toLocaleDateString('sv-SE',{timeZone:'Europe/Moscow'}).slice(0,7)+'-01'}
  async function loadSchedule(){const z=await dayoffApi('weekly');return {weekdays:Array.isArray(z.weekdays)?z.weekdays:[0,1,2,3,4,5,6],groups:Array.isArray(z.work_time_groups)?z.work_time_groups:[]}}
  function timesFor(ds,s){const dow=new Date(ds+'T12:00:00+03:00').getDay();if(s.groups.length){const g=s.groups.find(x=>(x.days||[]).includes(dow));return g?[...(g.times||[])]:[]}return s.weekdays.includes(dow)?['10:00','13:00','16:00','19:00']:[]}
  function overlap(start,end,a,b){return start<b&&end>a}
  async function collect(from,to){
    const s=await loadSchedule();
    const cal=await rpc('master_app_calendar',{p_pin:PIN,p_from:new Date(from+'T00:00:00+03:00').toISOString(),p_to:new Date(add(to,1)+'T00:00:00+03:00').toISOString()});
    const out=[]; let ds=from;
    while(ds<=to){
      const dow=new Date(ds+'T12:00:00+03:00').getDay();
      if(s.weekdays.includes(dow)){
        const aps=(cal.appointments||[]).filter(a=>a.status!=='cancelled');
        const blocks=(cal.blocks||[]);
        const ovs=(cal.overrides||[]).filter(o=>localDate(o.slot_start)===ds);
        const extra=ovs.filter(o=>o.is_available).map(o=>localTime(o.slot_start));
        const times=[...new Set([...timesFor(ds,s),...extra])].sort();
        const slots=[];
        for(const t of times){
          const iso=new Date(ds+'T'+t+':00+03:00').toISOString();
          const ov=ovs.find(o=>new Date(o.slot_start).getTime()===new Date(iso).getTime());
          if(ov&&ov.is_available===false)continue;
          const st=new Date(iso), en=new Date(st.getTime()+30*60000);
          const busy=aps.some(a=>overlap(st,en,new Date(a.starts_at),new Date(a.ends_at)))||blocks.some(b=>overlap(st,en,new Date(b.starts_at),new Date(b.ends_at)));
          if(!busy)slots.push(t);
        }
        if(slots.length)out.push({date:ds,times:slots});
      }
      ds=add(ds,1);
    }
    return out;
  }
  function presets(){const t=todayStr(),thisMon=monday(t),nextMon=add(thisMon,7),cm=monthStart(t),nm=nextMonth(t);return {week:[t,add(thisMon,6)],nextweek:[nextMon,add(nextMon,6)],month:[t,add(nextMonth(cm),-1)],nextmonth:[nm,add(nextMonth(nm),-1)]}}
  async function renderRange(key){
    const p=presets()[key]||presets().week,host=document.getElementById('winList');host.innerHTML='<div class="sub" style="padding:12px">Загрузка…</div>';
    try{const rows=await collect(p[0],p[1]);if(!rows.length){host.innerHTML='<div class="card">Свободных окошек в этом периоде нет.</div>';return}
      host.innerHTML=rows.map(r=>`<div class="win-day"><div class="win-date"><b>${dateWithDay(r.date)}</b><button type="button" class="win-day-all" data-date="${r.date}">Все</button></div><div class="win-times">${r.times.map(t=>`<label class="win-slot"><input type="checkbox" checked data-date="${r.date}" data-time="${t}"><span>${t}</span></label>`).join('')}</div></div>`).join('');
      host.querySelectorAll('.win-day-all').forEach(b=>b.onclick=()=>{const xs=[...host.querySelectorAll(`input[data-date="${b.dataset.date}"]`)];const on=xs.some(x=>!x.checked);xs.forEach(x=>x.checked=on)});
    }catch(e){host.innerHTML='<div class="card">Ошибка: '+e.message+'</div>'}
  }
  function selectedRows(){const xs=[...document.querySelectorAll('#winList .win-slot input:checked')];const by={};xs.forEach(x=>{(by[x.dataset.date]||(by[x.dataset.date]=[])).push(x.dataset.time)});return Object.keys(by).sort().map(ds=>({date:ds,times:by[ds].sort()}))}
  function selectedText(){const rows=selectedRows();if(!rows.length)return'';return 'Свободные окошки:\n\n'+rows.map(r=>`${dateWithDay(r.date)} — ${r.times.join(', ')}`).join('\n')}
  function roundRect(ctx,x,y,w,h,r){const rr=Math.min(r,w/2,h/2);ctx.beginPath();ctx.moveTo(x+rr,y);ctx.arcTo(x+w,y,x+w,y+h,rr);ctx.arcTo(x+w,y+h,x,y+h,rr);ctx.arcTo(x,y+h,x,y,rr);ctx.arcTo(x,y,x+w,y,rr);ctx.closePath()}
  function makeImage(){
    const rows=selectedRows();if(!rows.length){alert('Выберите хотя бы одно окошко');return null}
    const W=1080,pad=86,cardGap=30,cardH=150,headH=260,bottom=100,H=headH+rows.length*(cardH+cardGap)+bottom;
    const c=document.createElement('canvas');c.width=W;c.height=H;const x=c.getContext('2d');
    const g=x.createLinearGradient(0,0,W,H);g.addColorStop(0,'#f8eff4');g.addColorStop(.55,'#f4e7ef');g.addColorStop(1,'#efe2ea');x.fillStyle=g;x.fillRect(0,0,W,H);
    x.fillStyle='rgba(255,255,255,.34)';x.beginPath();x.arc(W-120,110,230,0,Math.PI*2);x.fill();x.beginPath();x.arc(70,H-40,190,0,Math.PI*2);x.fill();
    x.textAlign='center';x.fillStyle='#342d33';x.font='700 68px Georgia, serif';x.fillText('Свободные окошки',W/2,112);
    x.fillStyle='#7b6873';x.font='32px system-ui, sans-serif';x.fillText('Выберите удобное время',W/2,170);
    let y=headH;
    rows.forEach(r=>{
      x.fillStyle='rgba(255,255,255,.82)';roundRect(x,pad,y,W-pad*2,cardH,34);x.fill();
      x.strokeStyle='#d8bacb';x.lineWidth=2;roundRect(x,pad,y,W-pad*2,cardH,34);x.stroke();
      x.textAlign='left';x.fillStyle='#3d343a';x.font='700 38px Georgia, serif';x.fillText(dateWithDay(r.date),pad+38,y+55);
      x.fillStyle='#8b6d7d';x.font='700 36px system-ui, sans-serif';x.fillText(r.times.join('   ·   '),pad+38,y+110);
      y+=cardH+cardGap;
    });
    x.textAlign='center';x.fillStyle='#8c7a84';x.font='26px Georgia, serif';x.fillText('Татьяна · запись по договорённости',W/2,H-46);
    return c;
  }
  function downloadImage(){const c=makeImage();if(!c)return;c.toBlob(blob=>{if(!blob)return;const a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download='svobodnye-okoshki.png';document.body.appendChild(a);a.click();setTimeout(()=>{URL.revokeObjectURL(a.href);a.remove()},1000)},'image/png')}
  window.openWindows=function(){
    modal('Окошки','Выберите период и оставьте только те времена, которые хотите отправить клиенту',`<div class="win-presets"><button class="btn win-preset on" data-p="week">Эта неделя</button><button class="btn win-preset" data-p="nextweek">Следующая</button><button class="btn win-preset" data-p="month">Этот месяц</button><button class="btn win-preset" data-p="nextmonth">Следующий</button></div><div id="winList" class="win-list"></div>`,`<button class="btn primary" id="winCopy">Скопировать</button><button class="btn" id="winShare">Поделиться</button><button class="btn" id="winDownload">Скачать</button><button class="btn" id="winClose">Закрыть</button>`);
    let current='week';renderRange(current);document.querySelectorAll('.win-preset').forEach(b=>b.onclick=()=>{current=b.dataset.p;document.querySelectorAll('.win-preset').forEach(x=>x.classList.toggle('on',x===b));renderRange(current)});
    document.getElementById('winClose').onclick=closeModal;
    document.getElementById('winCopy').onclick=async()=>{const t=selectedText();if(!t)return alert('Выберите хотя бы одно окошко');try{await navigator.clipboard.writeText(t);document.getElementById('winCopy').textContent='Скопировано ✓';setTimeout(()=>{const x=document.getElementById('winCopy');if(x)x.textContent='Скопировать'},1200)}catch{prompt('Скопируйте текст:',t)}};
    document.getElementById('winShare').onclick=async()=>{const t=selectedText();if(!t)return alert('Выберите хотя бы одно окошко');if(navigator.share){try{await navigator.share({text:t})}catch{}}else{try{await navigator.clipboard.writeText(t);alert('Текст скопирован')}catch{prompt('Скопируйте текст:',t)}}};
    document.getElementById('winDownload').onclick=downloadImage;
  };
  const btn=document.getElementById('windowsBtn');if(btn)btn.onclick=()=>openWindows();
  const st=document.createElement('style');st.textContent='.win-presets{display:grid;grid-template-columns:1fr 1fr;gap:6px;margin:8px 0 10px}.win-preset.on{background:var(--soft);border-color:var(--accent);font-weight:700}.win-list{display:grid;gap:10px}.win-day{border:1px solid var(--line);border-radius:12px;padding:10px;background:#fff}.win-date{display:flex;justify-content:space-between;align-items:center;gap:8px}.win-day-all{border:0;background:none;color:var(--muted);font-size:12px}.win-times{display:flex;flex-wrap:wrap;gap:7px;margin-top:8px}.win-slot input{display:none}.win-slot span{display:block;border:1px solid var(--line);border-radius:999px;padding:7px 11px;background:#f5f3f4;font-size:13px}.win-slot input:checked+span{background:var(--soft);border-color:var(--accent);font-weight:700}';document.head.appendChild(st);
})();