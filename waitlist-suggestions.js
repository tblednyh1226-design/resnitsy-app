/* Automatic 1–2 slot suggestions inside waitlist cards. */
(function(){
  const MATCH_API='https://acukaqoguzkrphauovhk.supabase.co/functions/v1/resnitsy-waitlist-match';
  async function match(requestId){
    const r=await fetch(MATCH_API,{method:'POST',headers:{'Content-Type':'text/plain;charset=UTF-8'},body:JSON.stringify({pin:PIN,requestId}),cache:'no-store'});
    const j=await r.json();if(!r.ok||!j.ok)throw Error(j.error||'Не удалось подобрать окно');return j.slots||[];
  }
  function shortLabel(x){const d=new Date(x.start);return d.toLocaleDateString('ru-RU',{timeZone:'Europe/Moscow',day:'numeric',month:'short',weekday:'short'}).replace('.','')+' · '+d.toLocaleTimeString('ru-RU',{timeZone:'Europe/Moscow',hour:'2-digit',minute:'2-digit'})}
  async function enrich(){
    const cards=[...document.querySelectorAll('.wl-request-card')];
    await Promise.all(cards.map(async card=>{
      if(card.querySelector('.wl-suggest-box'))return;
      const id=card.dataset.request;if(!id)return;
      try{
        const slots=await match(id);if(!slots.length)return;
        const box=document.createElement('div');box.className='wl-suggest-box';
        box.innerHTML='<div class="wl-suggest-title">Подходящие окна</div><div class="wl-suggest-list"></div>';
        const list=box.querySelector('.wl-suggest-list');
        slots.slice(0,2).forEach(s=>{const b=document.createElement('button');b.className='btn wl-suggest-btn';b.textContent=shortLabel(s);b.onclick=async()=>{b.disabled=true;try{await waitApi('offer',{id,offeredAt:s.start,text:'Предложено подходящее окно '+shortLabel(s)});if(window.refreshWaitlistCounter)await window.refreshWaitlistCounter();if(window.openWaitlistOverview){closeModal();await window.openWaitlistOverview('active')}}catch(e){alert(e.message)}finally{b.disabled=false}};list.appendChild(b)});
        const actions=card.querySelector('.wl-actions');card.insertBefore(box,actions||null);
      }catch(e){console.warn('waitlist match',e)}
    }));
  }
  const old=window.openWaitlistOverview;
  if(typeof old==='function')window.openWaitlistOverview=async function(...args){const r=await old(...args);setTimeout(enrich,30);return r};
  const btn=document.getElementById('homeWaitlistBtn');if(btn){const oldClick=btn.onclick;btn.onclick=async e=>{if(oldClick)await oldClick.call(btn,e);setTimeout(enrich,30)}}
  const st=document.createElement('style');st.textContent='.wl-suggest-box{margin-top:8px;padding:8px;border-radius:10px;background:var(--soft);border:1px solid var(--accent)}.wl-suggest-title{font-size:11px;font-weight:800;margin-bottom:6px}.wl-suggest-list{display:flex;gap:6px;flex-wrap:wrap}.wl-suggest-btn{min-height:34px!important;padding:7px 10px!important;font-size:11px!important;background:#fff!important;flex:1}';document.head.appendChild(st);
})();
