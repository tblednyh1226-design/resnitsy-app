/* Telegram client linking in client card */
(function(){
 const SB='https://acukaqoguzkrphauovhk.supabase.co';
 const KEY='sb_publishable_2aKxmTx4WtnZglspnun9gA_goe71amD';
 const W='11111111-1111-4111-8111-111111111111';
 async function rpc(name,body){
   const r=await fetch(`${SB}/rest/v1/rpc/${name}`,{method:'POST',headers:{'Content-Type':'application/json','apikey':KEY,'Authorization':`Bearer ${KEY}`},body:JSON.stringify(body)});
   const j=await r.json().catch(()=>({}));
   if(!r.ok)throw Error(j.message||j.error||'Ошибка Telegram');
   return j;
 }
 const original=window.clientCard;
 if(typeof original!=='function')return;
 window.clientCard=async function(id){
   await original(id);
   const anchor=document.querySelector('.client-history-title');
   if(!anchor)return;
   const wrap=document.createElement('div');wrap.id='clientTelegramBox';wrap.className='card';wrap.style.marginTop='8px';wrap.innerHTML='<b>Telegram</b><div class="sub">Проверяю подключение…</div>';
   anchor.parentNode.insertBefore(wrap,anchor);
   try{
     const st=await rpc('master_telegram_link_status',{p_workspace:W,p_pin:PIN,p_client:id});
     if(st.linked){
       wrap.innerHTML=`<div style="display:flex;justify-content:space-between;gap:8px;align-items:center"><span><b>Telegram подключён ✓</b><div class="sub">${st.username?'@'+st.username:'Клиент получает сообщения через бота'}</div></span></div>`;
       return;
     }
     wrap.innerHTML='<div style="display:flex;justify-content:space-between;gap:8px;align-items:center"><span><b>Telegram не подключён</b><div class="sub">Создать персональную ссылку для клиента</div></span><button class="btn primary" id="clientTelegramLinkBtn">Подключить</button></div>';
     document.getElementById('clientTelegramLinkBtn').onclick=async function(){
       this.disabled=true;this.textContent='Создаю…';
       try{
         const z=await rpc('master_create_telegram_link',{p_workspace:W,p_pin:PIN,p_client:id});
         if(!z.url)throw Error('Ссылка не создана');
         location.href=z.url;
       }catch(e){alert(e.message);this.disabled=false;this.textContent='Подключить'}
     };
   }catch(e){wrap.innerHTML='<b>Telegram</b><div class="sub" style="color:#a33">'+e.message+'</div>'}
 };
})();