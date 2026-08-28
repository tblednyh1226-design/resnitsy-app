/* Notifications directly from appointment card: templates, share/copy and per-appointment history. */
(function(){
  if(typeof window.showAppt!=='function') return;
  const baseShow=window.showAppt;
  const typeNames={confirmation:'Подтверждение записи',reminder:'Напоминание',reschedule:'Перенос записи',cancellation:'Отмена записи',custom:'Свободное сообщение'};
  function esc(s){return String(s||'').replace(/[&<>"']/g,m=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m]))}
  function dateText(iso){return new Date(iso).toLocaleDateString('ru-RU',{timeZone:'Europe/Moscow',day:'numeric',month:'long',weekday:'short'})}
  function timeText(iso){return new Date(iso).toLocaleTimeString('ru-RU',{timeZone:'Europe/Moscow',hour:'2-digit',minute:'2-digit'})}
  function fill(t,a){const service=(a.services||[]).map(s=>s.name).join(' + ');return String(t||'').replaceAll('{name}',a.client_name||'').replaceAll('{service}',service).replaceAll('{date}',dateText(a.starts_at)).replaceAll('{time}',timeText(a.starts_at))}
  async function history(a){try{const z=await notifyApi('history',{appointmentId:a.id}),rows=z.rows||[];return rows}catch{return []}}
  function scheduleText(n,a){if(!n?.enabled)return 'Автоуведомления выключены';const parts=[];if(n.reminder_day_before)parts.push(`накануне в ${n.reminder_day_before_time||'19:00'}`);if(n.reminder_hours_before)parts.push(`за ${n.reminder_hours||3} ч.`);return parts.length?'Запланировано: '+parts.join(' и '):'Автоматические напоминания не запланированы'}
  async function openNotify(a){
    let z;try{z=await notifyApi('get')}catch(e){return alert(e.message)}
    const n=z.settings||{},h=await history(a),first='confirmation';
    const choices=[['confirmation','Подтверждение'],['reminder','Напоминание'],['reschedule','Перенос'],['cancellation','Отмена'],['custom','Свой текст']];
    modal('Написать клиенту',`${a.client_name||'Клиент'} · ${dateText(a.starts_at)} ${timeText(a.starts_at)}`,
      `<div class="an-auto"><b>Автоматические уведомления</b><div class="sub">${esc(scheduleText(n,a))}</div></div>
       <div class="an-types">${choices.map(([k,l],i)=>`<button class="btn an-type ${i===0?'on':''}" data-type="${k}">${l}</button>`).join('')}</div>
       <div class="field"><label>Сообщение</label><textarea id="anText" rows="8"></textarea></div>
       <div class="an-hint sub">Можно изменить текст перед отправкой.</div>
       <div class="an-history"><b>История сообщений по этой записи</b><div id="anHistory">${h.length?h.map(r=>`<div class="an-hrow"><div><b>${esc(typeNames[r.template_type]||r.template_type)}</b><div class="sub">${new Date(r.created_at).toLocaleString('ru-RU',{timeZone:'Europe/Moscow'})} · ${esc(r.channel)}</div></div><span>${r.delivery_status==='shared'?'отправлено':'подготовлено'}</span></div>`).join(''):'<div class="sub" style="margin-top:7px">Сообщений ещё не было</div>'}</div></div>`,
      `<button class="btn primary" id="anShare">Отправить</button><button class="btn" id="anCopy">Скопировать</button><button class="btn" id="anBack">Назад к записи</button>`);
    let type=first;const ta=document.getElementById('anText');
    function setType(k){type=k;document.querySelectorAll('.an-type').forEach(b=>b.classList.toggle('on',b.dataset.type===k));ta.value=k==='custom'?'':fill(n.templates?.[k]||'',a);ta.focus()}
    document.querySelectorAll('.an-type').forEach(b=>b.onclick=()=>setType(b.dataset.type));setType(first);
    document.getElementById('anBack').onclick=()=>{closeModal();baseShow(a)};
    document.getElementById('anCopy').onclick=async()=>{const text=ta.value.trim();if(!text)return alert('Введите сообщение');try{await navigator.clipboard.writeText(text);await notifyApi('log',{appointmentId:a.id,clientId:a.client_id,templateType:type,channel:'copy',message:text,status:'prepared'});const b=document.getElementById('anCopy');b.textContent='Скопировано ✓';setTimeout(()=>{if(document.getElementById('anCopy'))document.getElementById('anCopy').textContent='Скопировать'},1000)}catch{prompt('Скопируйте сообщение:',text)}};
    document.getElementById('anShare').onclick=async()=>{const text=ta.value.trim();if(!text)return alert('Введите сообщение');const b=document.getElementById('anShare');try{if(navigator.share){await navigator.share({text});await notifyApi('log',{appointmentId:a.id,clientId:a.client_id,templateType:type,channel:'share',message:text,status:'shared'});b.textContent='Отправлено ✓'}else{await navigator.clipboard.writeText(text);await notifyApi('log',{appointmentId:a.id,clientId:a.client_id,templateType:type,channel:'copy',message:text,status:'prepared'});alert('Текст скопирован — выберите мессенджер и отправьте клиенту')}}catch(e){if(e?.name!=='AbortError')alert(e.message||'Не удалось открыть отправку')}};
  }
  window.showAppt=function(a){baseShow(a);const footer=document.getElementById('mf');if(!footer)return;const b=document.createElement('button');b.className='btn primary an-open';b.textContent='Написать клиенту';b.onclick=()=>openNotify(a);footer.insertBefore(b,footer.firstChild)};
  const st=document.createElement('style');st.textContent='.an-open{grid-column:1/-1}.an-auto{border:1px solid var(--line);background:var(--soft);border-radius:12px;padding:10px;margin:8px 0}.an-types{display:flex;gap:6px;overflow:auto;padding:2px 0 8px;scrollbar-width:none}.an-type{white-space:nowrap;min-height:36px;font-size:11px}.an-type.on{background:var(--soft);border-color:var(--accent);font-weight:700}.an-history{border-top:1px solid var(--line);padding-top:10px;margin-top:10px}.an-hrow{display:flex;justify-content:space-between;gap:10px;padding:8px 0;border-bottom:1px solid #eee}.an-hrow span{font-size:10px;color:var(--muted);white-space:nowrap}.an-hint{margin-top:-4px}';document.head.appendChild(st);
})();