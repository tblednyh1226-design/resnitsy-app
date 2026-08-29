/* Telegram webhook completion UI */
(function(){
 const base=window.renderNotifyPanel;
 if(typeof base!=='function')return;
 window.renderNotifyPanel=async function(target){
   await base(target);
   try{
     const z=await notifyApi('get'),st=z.status||{},card=target.querySelector('.telegram-connect');
     if(!card||!st.telegram_token_configured)return;
     const box=document.createElement('div');box.className='telegram-webhook-state';box.style.marginTop='10px';
     if(st.telegram_webhook_configured){
       box.innerHTML='<div class="tg-connected">✓ Telegram полностью подключён</div>'+(st.telegram_webhook_error?'<div class="sub" style="color:#a33">Последняя ошибка Telegram: '+e(st.telegram_webhook_error)+'</div>':'');
     }else{
       box.innerHTML='<div class="sub" style="margin-bottom:7px">Нужно один раз завершить подключение, чтобы бот получал /start и связывал клиентов.</div><button class="btn primary" id="tgFinishWebhook" type="button" style="width:100%">Завершить подключение Telegram</button><div id="tgWebhookMsg" class="sub"></div>';
     }
     card.appendChild(box);
     const b=box.querySelector('#tgFinishWebhook');if(b)b.onclick=async()=>{const m=box.querySelector('#tgWebhookMsg');b.disabled=true;b.textContent='Подключаю…';try{await notifyApi('setupTelegramWebhook');m.textContent='Webhook подключён ✓';setTimeout(()=>window.renderNotifyPanel(target),500)}catch(err){m.textContent=err.message;b.disabled=false;b.textContent='Завершить подключение Telegram'}};
   }catch(err){console.warn('Telegram webhook UI:',err)}
 };
})();
