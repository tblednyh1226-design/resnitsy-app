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
     const renderState=(ok,msg)=>{box.innerHTML=`<div class="tg-connected" style="${ok?'':'background:#fff1f1;color:#a33'}">${ok?'✓ ':''}${e(msg)}</div>`};
     if(st.telegram_webhook_configured){renderState(true,'Webhook подключён — бот готов получать сообщения');}
     else{
       box.innerHTML='<div class="sub" style="margin-bottom:7px">Завершите подключение, чтобы бот получал /start и связывал клиентов.</div><button class="btn primary" id="tgFinishWebhook" type="button" style="width:100%">Завершить подключение Telegram</button><div id="tgWebhookMsg" class="sub" style="margin-top:8px"></div>';
       const b=box.querySelector('#tgFinishWebhook');
       b.onclick=async()=>{const m=box.querySelector('#tgWebhookMsg');b.disabled=true;b.textContent='Проверяю Telegram…';m.textContent='';try{const r=await notifyApi('setupTelegramWebhook');const check=await notifyApi('get');if(check.status?.telegram_webhook_configured){renderState(true,'Webhook подключён — бот готов получать сообщения');}else{throw Error(r?.description||check.status?.telegram_webhook_error||'Telegram не подтвердил webhook');}}catch(err){m.textContent='Ошибка: '+err.message;b.disabled=false;b.textContent='Повторить подключение Telegram';}};
     }
     card.appendChild(box);
   }catch(err){const card=target.querySelector('.telegram-connect');if(card){const d=document.createElement('div');d.className='sub';d.style.cssText='color:#a33;margin-top:8px';d.textContent='Не удалось проверить webhook: '+err.message;card.appendChild(d)}}
 };
})();
