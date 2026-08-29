/* Notification and message settings */
const NOTIFY_EDGE='https://acukaqoguzkrphauovhk.supabase.co/functions/v1/resnitsy-notifications';
async function notifyApi(action,data={}){const r=await fetch(NOTIFY_EDGE,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({pin:PIN,action,...data})});const j=await r.json();if(!r.ok||!j.ok)throw Error(j.error||'Ошибка настроек уведомлений');return j}
const _renderSettings_notify=renderSettings;
renderSettings=async function(){await _renderSettings_notify();const box=document.getElementById('settingsBox');box.querySelectorAll('.notify-settings-block').forEach(x=>x.remove());let z;try{z=await notifyApi('get')}catch(e){const er=document.createElement('div');er.className='card notify-settings-block';er.innerHTML='<b>Уведомления</b><div class="sub" style="color:#a33">'+e.message+'</div>';box.appendChild(er);return}const n=z.settings||{},st=z.status||{},mp=n.master_push||{};const card=document.createElement('div');card.className='card notify-settings-block';card.innerHTML=`
<b>Уведомления</b><div class="sub">Автоматические сообщения клиентам и уведомления мастеру</div>
<label class="notify-row notify-master"><span><b>Автоуведомления клиентам</b><small>Главный переключатель автоматической отправки</small></span><input id="ntEnabled" type="checkbox" ${n.enabled?'checked':''}></label>
<div class="notify-section"><b>События для клиента</b>
<label class="notify-row"><span><b>Подтверждение записи</b><small>После онлайн-записи или подтверждения мастером</small></span><input id="ntConfirmOn" type="checkbox" ${n.confirmation_enabled!==false?'checked':''}></label>
<label class="notify-row"><span><b>Перенос записи</b><small>Сообщить клиенту о новой дате или времени</small></span><input id="ntMoveOn" type="checkbox" ${n.reschedule_enabled!==false?'checked':''}></label>
<label class="notify-row"><span><b>Отмена записи</b><small>Сообщить клиенту об отмене</small></span><input id="ntCancelOn" type="checkbox" ${n.cancellation_enabled!==false?'checked':''}></label>
</div>
<div class="notify-section"><b>Напоминания о визите</b>
<label class="notify-row"><span><b>Накануне</b><small>Отправить за день до визита</small></span><input id="ntDayBefore" type="checkbox" ${n.reminder_day_before?'checked':''}></label>
<div class="field"><label>Время отправки накануне</label><input id="ntDayTime" type="time" value="${n.reminder_day_before_time||'19:00'}"></div>
<label class="notify-row"><span><b>Дополнительное напоминание</b><small>За несколько часов до визита</small></span><input id="ntHoursOn" type="checkbox" ${n.reminder_hours_before?'checked':''}></label>
<div class="field"><label>За сколько часов</label><input id="ntHours" type="number" min="1" max="48" value="${n.reminder_hours||3}"></div>
</div>
<div class="notify-section"><b>Каналы связи с клиентом</b><div class="sub">Для автоматической отправки канал должен быть технически подключён.</div>
${channelRow('Telegram','telegram',n.channels?.telegram,st.telegram_token_configured)}
${channelRow('WhatsApp','whatsapp',n.channels?.whatsapp,st.whatsapp_configured)}
${channelRow('VK','vk',n.channels?.vk,st.vk_configured)}
${channelRow('MAX','max',n.channels?.max,st.max_configured)}
</div>
<div class="notify-section master-push-section"><b>Уведомления мастеру</b><div class="sub">Push-уведомления приложения о важных событиях. Настройки уже сохраняются; сама доставка push будет подключена при установочной версии приложения.</div>
<label class="notify-row notify-master"><span><b>Push-уведомления</b><small>Общее включение уведомлений мастеру</small></span><input id="mpEnabled" type="checkbox" ${mp.enabled!==false?'checked':''}></label>
${masterPushRow('Новая онлайн-запись','Показать сразу после новой записи клиента','mpNewBooking',mp.new_booking!==false)}
${masterPushRow('Изменение записи','Клиент или система изменили дату, время или услугу','mpChanged',mp.booking_changed!==false)}
${masterPushRow('Отмена записи','Клиент отменил запись','mpCancelled',mp.booking_cancelled!==false)}
${masterPushRow('Ловец окошек — новая заявка','Клиент добавился в список ожидания','mpWaitNew',mp.waitlist_new!==false)}
${masterPushRow('Ловец окошек — ответ клиента','Клиент принял, отклонил или ответил на предложенное окно','mpWaitResponse',mp.waitlist_response!==false)}
${masterPushRow('Сообщение клиенту отправлено','Уведомить об успешной автоматической отправке','mpMessageSent',!!mp.message_sent)}
${masterPushRow('Ошибка отправки сообщения','Уведомить, если сообщение клиенту не доставлено','mpMessageFailed',mp.message_failed!==false)}
${masterPushRow('Ответ техподдержки','Появился новый ответ по обращению','mpSupportReply',mp.support_reply!==false)}
</div>
<div class="notify-section"><b>Шаблоны сообщений</b><div class="sub">Откройте шаблон, измените текст и сохраните. Доступны переменные: {name}, {service}, {date}, {time}</div>
${templateBlock('Подтверждение записи','ntConfirm',n.templates?.confirmation,'confirmation')}
${templateBlock('Напоминание','ntReminder',n.templates?.reminder,'reminder')}
${templateBlock('Перенос записи','ntMove',n.templates?.reschedule,'reschedule')}
${templateBlock('Отмена записи','ntCancel',n.templates?.cancellation,'cancellation')}
</div>
<div class="notify-section"><b>Ручные сообщения</b><div class="sub">Кнопка «Написать клиенту» в карточке записи работает отдельно и не зависит от автоуведомлений.</div></div>
<div id="ntMsg" class="sub" style="min-height:20px"></div><button class="btn primary" id="ntSave" style="width:100%">Сохранить все настройки</button>
<div class="notify-status"><b>Подключение каналов</b><div class="sub">Telegram: ${st.telegram_token_configured?'подключён технически':'не подключён'}. WhatsApp: ${st.whatsapp_configured?'подключён':'не подключён'}. VK: ${st.vk_configured?'подключён':'не подключён'}. MAX: ${st.max_configured?'подключён':'не подключён'}.</div>${st.telegram_token_configured?'<button class="btn" id="ntTest" style="margin-top:7px;width:100%">Тест Telegram</button>':''}</div>`;box.appendChild(card);
function collectSettings(){return {enabled:document.getElementById('ntEnabled').checked,confirmation_enabled:document.getElementById('ntConfirmOn').checked,reschedule_enabled:document.getElementById('ntMoveOn').checked,cancellation_enabled:document.getElementById('ntCancelOn').checked,reminder_day_before:document.getElementById('ntDayBefore').checked,reminder_day_before_time:document.getElementById('ntDayTime').value,reminder_hours_before:document.getElementById('ntHoursOn').checked,reminder_hours:Number(document.getElementById('ntHours').value||3),channels:{telegram:document.getElementById('ntChtelegram').checked,whatsapp:document.getElementById('ntChwhatsapp').checked,vk:document.getElementById('ntChvk').checked,max:document.getElementById('ntChmax').checked},master_push:{enabled:document.getElementById('mpEnabled').checked,new_booking:document.getElementById('mpNewBooking').checked,booking_changed:document.getElementById('mpChanged').checked,booking_cancelled:document.getElementById('mpCancelled').checked,waitlist_new:document.getElementById('mpWaitNew').checked,waitlist_response:document.getElementById('mpWaitResponse').checked,message_sent:document.getElementById('mpMessageSent').checked,message_failed:document.getElementById('mpMessageFailed').checked,support_reply:document.getElementById('mpSupportReply').checked},templates:{confirmation:document.getElementById('ntConfirm').value,reminder:document.getElementById('ntReminder').value,reschedule:document.getElementById('ntMove').value,cancellation:document.getElementById('ntCancel').value}}}
async function saveAll(btn,msg){btn.disabled=true;const old=btn.textContent;btn.textContent='Сохраняю…';if(msg)msg.textContent='';try{await notifyApi('save',{settings:collectSettings()});btn.textContent='Сохранено ✓';if(msg)msg.textContent='Изменения сохранены';setTimeout(()=>{btn.disabled=false;btn.textContent=old},1100)}catch(e){if(msg)msg.textContent=e.message;btn.disabled=false;btn.textContent=old;throw e}}
document.getElementById('ntSave').onclick=async()=>{try{await saveAll(document.getElementById('ntSave'),document.getElementById('ntMsg'))}catch(e){}};
card.querySelectorAll('[data-save-template]').forEach(btn=>{btn.onclick=async()=>{const status=btn.closest('.notify-template')?.querySelector('.template-save-status');try{await saveAll(btn,status)}catch(e){if(status)status.textContent=e.message}}});
card.querySelectorAll('.notify-template textarea').forEach(t=>{t.addEventListener('input',()=>{const s=t.closest('.notify-template')?.querySelector('.template-save-status');if(s)s.textContent='Есть несохранённые изменения'})});
const test=document.getElementById('ntTest');if(test)test.onclick=async()=>{const chat=prompt('Введите Telegram chat_id для теста');if(!chat)return;test.disabled=true;test.textContent='Отправляю…';try{await notifyApi('testTelegram',{chat_id:chat,text:'Тестовое сообщение из приложения'});alert('Тестовое сообщение отправлено')}catch(e){alert(e.message)}finally{test.disabled=false;test.textContent='Тест Telegram'}}};
function channelRow(name,key,on,connected){return `<label class="notify-row"><span><b>${name}</b><small>${connected?'подключён':'не подключён'}</small></span><input id="ntCh${key}" type="checkbox" ${on?'checked':''}></label>`}
function masterPushRow(title,sub,id,on){return `<label class="notify-row"><span><b>${title}</b><small>${sub}</small></span><input id="${id}" type="checkbox" ${on?'checked':''}></label>`}
function templateBlock(title,id,value,key){return `<details class="notify-template"><summary>${title}</summary><div class="field"><textarea id="${id}" rows="5">${String(value||'').replace(/&/g,'&amp;').replace(/</g,'&lt;')}</textarea></div><div class="template-actions"><span class="sub template-save-status"></span><button class="btn" type="button" data-save-template="${key}">Сохранить шаблон</button></div></details>`}
const style=document.createElement('style');style.textContent='.notify-template{border-top:1px solid var(--line);padding:10px 0}.notify-template summary{cursor:pointer;font-weight:700;list-style:none}.notify-template summary::-webkit-details-marker{display:none}.notify-template summary:after{content:"›";float:right;color:var(--muted)}.notify-template[open] summary:after{transform:rotate(90deg)}.notify-master{background:var(--soft);padding:12px;border-radius:12px;border-bottom:0}.notify-status{margin-top:12px}.notify-template textarea{width:100%;min-height:110px;resize:vertical}.template-actions{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-top:7px}.template-actions .sub{flex:1}.template-actions .btn{white-space:nowrap}.master-push-section{margin-top:16px}.master-push-section>.sub{margin:4px 0 10px}';document.head.appendChild(style);
