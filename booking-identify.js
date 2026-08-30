/* Phone-first identification for online booking */
(function(){
  const ID_API='https://acukaqoguzkrphauovhk.supabase.co/functions/v1/resnitsy-client-identity';
  async function identify(phone){
    const r=await fetch(ID_API,{method:'POST',headers:{'Content-Type':'text/plain;charset=UTF-8'},body:JSON.stringify({phone}),cache:'no-store'});
    const j=await r.json();
    if(!r.ok||!j.ok)throw Error(j.error||'Не удалось проверить номер');
    return j;
  }
  function makeSection(id,html){const s=document.createElement('section');s.id=id;s.className='step';s.innerHTML=html;return s}
  const s1=document.getElementById('s1'); if(!s1)return;
  document.querySelectorAll('.step').forEach(x=>x.classList.remove('on'));
  const s0=makeSection('s0','<div class="scroll"><h3>Контактные данные</h3><div class="field"><label>Имя</label><input id="identityName" autocomplete="name" placeholder="Имя"></div><div class="field"><label>Телефон</label><input id="identityPhone" type="tel" autocomplete="tel" inputmode="tel" value="+7 " placeholder="+7 999 123-45-67"></div><div id="identityError"></div></div><div class="actions one"><button id="identityNext" class="btn primary">Далее</button></div>');
  const sl=makeSection('s0last','<div class="scroll"><h3>Последняя услуга</h3><div id="lastServiceBox" class="summary" style="margin-top:10px"></div></div><div class="actions"><button id="chooseOther" class="btn">Выбрать другую</button><button id="repeatLast" class="btn primary">Повторить</button></div>');
  s1.parentNode.insertBefore(sl,s1); s1.parentNode.insertBefore(s0,sl); s0.classList.add('on');
  const nameInput=document.getElementById('identityName'),phoneInput=document.getElementById('identityPhone'),next=document.getElementById('identityNext'),err=document.getElementById('identityError');
  let identity=null;
  phoneInput.addEventListener('input',()=>{if(!phoneInput.value.startsWith('+7 ')){let d=phoneInput.value.replace(/\D/g,'');if(d.startsWith('8')||d.startsWith('7'))d=d.slice(1);phoneInput.value='+7 '+d}});
  phoneInput.addEventListener('blur',()=>{if(phoneInput.value.replace(/\D/g,'')==='7')phoneInput.value='+7 '});
  function applyIdentity(j){
    identity=j;
    const p=phoneInput.value.trim(),n=nameInput.value.trim();
    const cp=document.getElementById('clientPhone'),wp=document.getElementById('wishPhone'),cn=document.getElementById('clientName'),wn=document.getElementById('wishName');
    if(cp){cp.value=p;cp.readOnly=true} if(wp)wp.value=p;
    if(cn){cn.value=n;cn.readOnly=true} if(wn)wn.value=n;
    if(j.messenger){const m=document.getElementById('messenger'),wm=document.getElementById('wishMessenger');if(m)m.value=j.messenger;if(wm)wm.value=j.messenger}
  }
  next.onclick=async()=>{const p=phoneInput.value.trim(),n=nameInput.value.trim();if(!n){err.innerHTML='<div class="error">Введите имя</div>';return}if(p.replace(/\D/g,'').length<11){err.innerHTML='<div class="error">Проверьте номер телефона</div>';return}next.disabled=true;next.textContent='Проверяю…';err.innerHTML='';try{const j=await identify(p);applyIdentity(j);if(j.client_found&&j.last_service){document.getElementById('lastServiceBox').innerHTML='<strong style="display:block;font-size:19px">'+j.last_service.name+'</strong>';step('s0last')}else step('s1')}catch(e){err.innerHTML='<div class="error">'+e.message+'</div>'}finally{next.disabled=false;next.textContent='Далее'}};
  phoneInput.addEventListener('keydown',e=>{if(e.key==='Enter')next.click()});
  nameInput.addEventListener('keydown',e=>{if(e.key==='Enter')phoneInput.focus()});
  document.getElementById('chooseOther').onclick=()=>{selected.clear();document.querySelectorAll('#serviceList .card').forEach(x=>x.classList.remove('on'));document.getElementById('toPeriods').disabled=true;step('s1')};
  document.getElementById('repeatLast').onclick=()=>{if(!identity?.last_service)return step('s1');selected.clear();selected.add(identity.last_service.id);document.querySelectorAll('#serviceList .card').forEach(x=>x.classList.toggle('on',x.textContent.includes(identity.last_service.name)));document.getElementById('toPeriods').disabled=false;step('s2')};
  const change=document.createElement('button');change.type='button';change.className='btn';change.style='min-height:38px;padding:6px 10px;margin:4px 0 8px';change.textContent='← Изменить данные';change.onclick=()=>step('s0');const sc=s1.querySelector('.scroll');if(sc)sc.insertBefore(change,sc.firstChild);
})();
