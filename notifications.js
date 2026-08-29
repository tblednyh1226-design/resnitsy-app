/* Notification and message settings */
const NOTIFY_EDGE='https://acukaqoguzkrphauovhk.supabase.co/functions/v1/resnitsy-notifications';
async function notifyApi(action,data={}){const r=await fetch(NOTIFY_EDGE,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({pin:PIN,action,...data})});const j=await r.json();if(!r.ok||!j.ok)throw Error(j.error||'Ошибка настроек уведомлений');return j}
const _renderSettings_notify=renderSettings;
renderSettings=async function(){await _renderSettings_notify();const box=document.getElementById('settingsBox');box.querySelectorAll('.notify-settings-block').forEach(x=>x.remove());let z;try{z=await notifyApi('get')}catch(e){const er=document.createElement('div');er.className='card notify-settings-block';er.innerHTML='<b>Уведомления</b><div class="sub" style="color:#a33">'+e.message+'</div>';box.appendChild(er);return}const n=z.settings||{},st=z.status||{};const card=document.createElement('div');card.className='card notify-settings-block';card.innerHTML=`
<b>Уведомления</b><div class="sub">Автоматические сообщения клиентам</div>
<label class="notify-row notify-master"><span><b>Автоуведомления</b><small>Главный переключатель автоматической отправки</small></span><input id="ntEnabled" type="checkbox" ${n.enabled?'checked':''}></label>
<div class="notify-section"><b>События</b>
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
<div class="notify-section"><b>Каналы связи</b><div class="sub">Для автоматической отправки канал должен быть технически подключён.</div>
${channelRow('Telegram','telegram',n.channels?.telegram,st.telegram_token_configured)}
${channelRow('WhatsApp','whatsapp',n.channels?.whatsapp,st.whatsapp_configured)}
${channelRow('VK','vk',n.channels?.vk,st.vk_configured)}
${channelRow('MAX','max',n.channels?.max,st.max_configured)}
</div>
<div class="notify-section"><b>Шаблоны сообщений</b><div class="sub">Доступны переменные: {name}, {service}, {date}, {time}</div>
<details class="notify-template"><summary>Подтверждение записи</summary>${tmpl('', 'ntConfirm',n.templates?.confirmation)}</details>
<details class="notify-template"><summary>Напоминание</summary>${tmpl('', 'ntReminder',n.templates?.reminder)}</details>
<details class="notify-template"><summary>Перенос записи</summary>${tmpl('', 'ntMove',n.templates?.reschedule)}</details>
<details class="notify-template"><summary>Отмена записи</summary>${tmpl('', 'ntCancel',n.templates?.cancellation)}</details>
</div>
<div class="notify-section"><b>Ручные сообщения</b><div class="sub">Кнопка «Написать клиенту» в карточке записи работает отдельно и не зависит от автоуведомлений.</div></div>
<div id="ntMsg" class="sub" style="min-height:20px"></div><button class="btn primary" id="ntSave" style="width:100%">Сохранить уведомления</button>
<div class="notify-status"><b>Подключение каналов</b><div class="sub">Telegram: ${st.telegram_token_configured?'подключён технически':'не подключён'}. WhatsApp: ${st.whatsapp_configured?'подключён':'не подключён'}. VK: ${st.vk_configured?'подключён':'не подключён'}. MAX: ${st.max_configured?'подключён':'не подключён'}.</div>${st.telegram_token_configured?'<button class="btn" id="ntTest" style="margin-top:7px;width:100%">Тест Telegram</button>':''}</div>`;box.appendChild(card);
document.getElementById('ntSave').onclick=async()=>{const btn=document.getElementById('ntSave'),msg=document.getElementById('ntMsg');btn.disabled=true;btn.textContent='Сохраняю…';msg.textContent='';try{await notifyApi('save',{settings:{enabled:document.getElementById('ntEnabled').checked,confirmation_enabled:document.getElementById('ntConfirmOn').checked,reschedule_enabled:document.getElementById('ntMoveOn').checked,cancellation_enabled:document.getElementById('ntCancelOn').checked,reminder_day_before:document.getElementById('ntDayBefore').checked,reminder_day_before_time:document.getElementById('ntDayTime').value,reminder_hours_before:document.getElementById('ntHoursOn').checked,reminder_hours:Number(document.getElementById('ntHours').value||3),channels:{telegram:document.getElementById('ntChtelegram').checked,whatsapp:document.getElementById('ntChwhatsapp').checked,vk:document.getElementById('ntChvk').checked,max:document.getElementById('ntChmax').checked},templates:{confirmation:document.getElementById('ntConfirm').value,reminder:document.getElementById('ntReminder').value,reschedule:document.getElementById('ntMove').value,cancellation:document.getElementById('ntCancel').value}}});msg.textContent='Настройки сохранены';btn.textContent='Сохранено';setTimeout(()=>{btn.disabled=false;btn.textContent='Сохранить уведомления'},900)}catch(e){msg.textContent=e.message;btn.disabled=false;btn.textContent='Сохранить уведомления'}};
const test=document.getElementById('ntTest');if(test)test.onclick=async()=>{const chat=prompt('Введите Telegram chat_id для теста');if(!chat)return;test.disabled=true;test.textContent='Отправляю…';try{await notifyApi('testTelegram',{chat_id:chat,text:'Тестовое сообщение из приложения'});alert('Тестовое сообщение отправлено')}catch(e){alert(e.message)}finally{test.disabled=false;test.textContent='Тест Telegram'}}};
function channelRow(name,key,on,connected){return `<label class="notify-row"><span><b>${name}</b><small>${connected?'подключён':'не подключён'}</small></span><input id="ntCh${key}" type="checkbox" ${on?'checked':''}></label>`}
function tmpl(label,id,value=''){return `<div class="field">${label?'<label>'+label+'</label>':''}<textarea id="${id}" rows="4">${String(value||'').replace(/&/g,'&amp;').replace(/</g,'&lt;')}</textarea></div>`}
const style=document.createElement('style');style.textContent='.notify-template{border-top:1px solid var(--line);padding:10px 0}.notify-template summary{cursor:pointer;font-weight:700;list-style:none}.notify-template summary::-webkit-details-marker{display:none}.notify-template summary:after{content:"›";float:right;color:var(--muted)}.notify-template[open] summary:after{transform:rotate(90deg)}.notify-master{background:var(--soft);padding:12px;border-radius:12px;border-bottom:0}.notify-status{margin-top:12px}';document.head.appendChild(style);
