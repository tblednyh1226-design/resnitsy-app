/* Settings hub: render shell immediately and isolate work/notification sections. */
(function(){
 let onlineEnabled=true,section='hub';
 function box(){return document.getElementById('settingsBox')}
 function menu(title,sub,kind){return `<button class="card settings-menu-card" data-settings-section="${kind}"><span><b>${title}</b><small>${sub}</small></span><i>›</i></button>`}
 function hubHtml(){return `<div class="settings-hub"><div class="card online-toggle-card"><span><b>Онлайн-запись</b><small id="onlineState">${onlineEnabled?'Включена':'Выключена'}</small></span><label class="switch"><input id="onlineBookingToggle" type="checkbox" ${onlineEnabled?'checked':''}><i></i></label></div>${menu('Режим работы','Рабочее время по дням, доступные часы, выходные и отпуск','work')}${menu('Уведомления','Напоминания, каналы и шаблоны сообщений','notify')}</div>`}
 function paintHub(){const b=box();if(!b)return;section='hub';b.innerHTML=hubHtml();bindHub(b)}
 function bindHub(b){b.querySelectorAll('[data-settings-section]').forEach(x=>x.onclick=()=>openSection(x.dataset.settingsSection));const t=b.querySelector('#onlineBookingToggle');if(t)t.onchange=e=>{onlineEnabled=e.target.checked;b.querySelector('#onlineState').textContent=onlineEnabled?'Включена':'Выключена'}}
 async function openSection(kind){const b=box();if(!b)return;section=kind;b.innerHTML='<button class="btn settings-back">‹ Настройки</button><div class="settings-loading sub">Загрузка…</div>';b.querySelector('.settings-back').onclick=paintHub;
   const stage=document.createElement('div');stage.className='settings-stage';stage.style.display='none';b.appendChild(stage);
   const oldId=b.id;b.id='settingsBoxVisible';stage.id='settingsBox';
   try{if(typeof window.__legacyRenderSettings==='function')await window.__legacyRenderSettings();}
   catch(e){console.error(e)}
   finally{stage.id='';b.id=oldId}
   const wanted=[...stage.children].filter(x=>kind==='work'?(x.classList?.contains('dayoff-settings-block')||x.classList?.contains('wt-settings')):x.classList?.contains('notify-settings-block'));
   b.querySelector('.settings-loading')?.remove();wanted.forEach(x=>{x.style.display='block';b.appendChild(x)});stage.remove();
   if(!wanted.length){const d=document.createElement('div');d.className='card';d.textContent=kind==='work'?'Не удалось загрузить режим работы':'Не удалось загрузить уведомления';b.appendChild(d)}
 }
 const legacy=window.renderSettings;window.__legacyRenderSettings=legacy;
 window.renderSettings=function(){paintHub()};
 document.addEventListener('click',e=>{const target=e.target.closest('[data-go="settings"],[data-s="settings"]');if(!target)return;const b=box();if(b)paintHub()},true);
 const st=document.createElement('style');st.textContent=`#settingsBox:empty{visibility:hidden}.settings-hub{display:grid;gap:8px}.settings-menu-card,.online-toggle-card{width:100%;border:1px solid var(--line);background:#fff;border-radius:14px;padding:15px 16px;text-align:left;display:flex;align-items:center;justify-content:space-between;gap:12px;color:inherit}.settings-menu-card span,.online-toggle-card>span{display:flex;flex-direction:column;gap:3px}.settings-menu-card b,.online-toggle-card b{font-size:17px}.settings-menu-card small,.online-toggle-card small{font-size:12px;color:var(--muted);font-style:normal}.settings-menu-card i{font-style:normal;font-size:27px;color:#aaa}.settings-back{margin-bottom:8px}.switch{position:relative;width:48px;height:28px;flex:0 0 auto}.switch input{display:none}.switch i{position:absolute;inset:0;border-radius:999px;background:#ddd}.switch i:after{content:'';position:absolute;width:22px;height:22px;left:3px;top:3px;border-radius:50%;background:#fff;box-shadow:0 1px 3px #999;transition:.15s}.switch input:checked+i{background:#a97a9d}.switch input:checked+i:after{transform:translateX(20px)}`;document.head.appendChild(st);
})();