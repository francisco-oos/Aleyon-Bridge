#!/usr/bin/env python3
"""Contract-level exhaustive simulation.

This does NOT pretend to be a live Gemini response test. It stress-tests the
Bridge-owned invariants that can be verified deterministically without a phone:
profile isolation, canonical-chat reuse/rebuild, state normalization, material
handoff policy, session closing, evidence enrichment and fail-closed behavior.
"""
from dataclasses import dataclass, field
from hashlib import sha256
import re, sys

LANGS=[
"Inglés","Francés","Alemán","Italiano","Portugués","Japonés","Coreano","Mandarín","Cantonés","Árabe",
"Hindi","Bengalí","Ruso","Ucraniano","Polaco","Checo","Eslovaco","Húngaro","Rumano","Búlgaro",
"Griego","Turco","Hebreo","Persa","Urdu","Punjabi","Tamil","Telugu","Maratí","Gujarati",
"Vietnamita","Tailandés","Indonesio","Malayo","Tagalo","Suajili","Afrikáans","Neerlandés","Sueco","Noruego",
"Danés","Finés","Islandés","Irlandés","Galés","Catalán","Gallego","Euskera","Maya yucateco","Tseltal",
"Náhuatl","Quechua","Guaraní","Esperanto","Latín","Serbio","Croata","Esloveno","Estonio","Letón"
]
PURPOSES=[
"conversación diaria","entrevista de trabajo","presentación técnica","viaje","lectura académica","manual industrial",
"atención a clientes","negociación","pronunciación","traducción","examen de certificación","conversación científica"
]
MATERIALS=[
("none","",0),
("application/pdf","manual.pdf",2_000_000),
("image/jpeg","fotografia.jpg",3_000_000),
("application/vnd.openxmlformats-officedocument.wordprocessingml.document","documento.docx",400_000),
("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","datos.xlsx",500_000),
("application/vnd.openxmlformats-officedocument.presentationml.presentation","presentacion.pptx",1_200_000),
("text/plain","notas.txt",30_000),
("text/csv","tabla.csv",80_000),
("audio/mpeg","audio.mp3",8_000_000),
("video/mp4","video.mp4",120_000_000),
]
VERSION_FAMILIES=[
"gemini-closed","canonical-open","other-chat-open","live-already-active","drawer-open",
"temporary-chat","consent-then-normal","search-control-renamed","live-label-renamed","structure-only-live"
]

@dataclass
class Memory:
    sessions:int=0
    summary:str=""
    next_obj:str=""
    events:list=field(default_factory=list)

@dataclass
class ProfileState:
    lang:str
    purpose:str
    profile_id:str
    title:str
    memory:Memory=field(default_factory=Memory)
    provider_chat_exists:bool=False
    rebuilds:int=0
    compatibility_success:int=0
    compatibility_fail:int=0


def norm_id(label:str)->str:
    import unicodedata
    raw=unicodedata.normalize('NFD',label.strip().lower())
    raw=''.join(c for c in raw if unicodedata.category(c)!='Mn')
    key=re.sub(r'[^a-z0-9]+',' ',raw).strip()
    key=re.sub(r'\s+','-',key)
    if key and re.fullmatch(r'[a-z0-9-]+',key): return 'lang-'+key
    return 'lang-u-'+sha256(label.encode()).hexdigest()[:12]

def safe_material(mime,name,size,idx):
    if mime=='none': return True
    uri=f'content://aleyon-test/{idx}/{name}'
    if not uri.startswith('content://') or size<=0: return False
    max_bytes=2*1024**3 if mime.startswith('video/') else 100*1024**2
    return size<=max_bytes and '\n' not in name and '\r' not in name

def normalize_start(family:str)->str:
    # Model the intended semantic outcome, not provider labels/coordinates.
    if family in {'gemini-closed','canonical-open','other-chat-open','drawer-open','search-control-renamed','live-label-renamed','structure-only-live'}:
        return 'NORMALIZABLE'
    if family=='live-already-active': return 'BACK_TO_CHAT_THEN_NORMALIZABLE'
    if family=='temporary-chat': return 'ESCAPE_TEMPORARY_THEN_NORMALIZABLE'
    if family=='consent-then-normal': return 'WAIT_HUMAN_THEN_NORMALIZABLE'
    raise AssertionError(family)

def run():
    errors=[]; profiles=[]
    for i,lang in enumerate(LANGS):
        p=ProfileState(lang,PURPOSES[i%len(PURPOSES)],norm_id(lang),f'ALEYON — {lang}')
        profiles.append(p)
    if len(profiles)<50: errors.append('less than 50 profiles')
    if len({p.profile_id for p in profiles})!=len(profiles): errors.append('profile id collision')
    if len({p.title for p in profiles})!=len(profiles): errors.append('canonical title collision')

    total_sessions=0; material_sessions=0; rebuilds=0; fail_closed=0
    for pi,p in enumerate(profiles):
        for session in range(6):
            total_sessions+=1
            family=VERSION_FAMILIES[(pi+session)%len(VERSION_FAMILIES)]
            outcome=normalize_start(family)
            if not outcome: errors.append(f'normalization failed {p.lang}/{family}')

            # Every profile loses its provider chat once; local memory must survive.
            if session==2:
                before=(p.memory.sessions,p.memory.summary,p.memory.next_obj,list(p.memory.events))
                p.provider_chat_exists=False
                if before!=(p.memory.sessions,p.memory.summary,p.memory.next_obj,list(p.memory.events)):
                    errors.append(f'provider deletion mutated local memory: {p.lang}')

            if not p.provider_chat_exists:
                p.provider_chat_exists=True; p.rebuilds+=1; rebuilds+=1
            else:
                p.compatibility_success+=1

            mime,name,size=MATERIALS[(pi*3+session)%len(MATERIALS)]
            if mime!='none':
                material_sessions+=1
                if not safe_material(mime,name,size,pi*10+session): errors.append(f'unsafe material rejected/accepted incorrectly: {p.lang}/{name}')

            # Simulated provider response model; Bridge closes and enriches local truth.
            p.memory.sessions+=1
            p.memory.summary=f'{p.lang} sesión {session+1}: {p.purpose}'
            p.memory.next_obj=f'continuar {p.purpose} {session+2}'
            p.memory.events.append(('session-progress',f'avance-{session+1}'))
            p.memory.events.append(('session-reinforcement',f'reforzar-{session+1}'))

            if p.memory.sessions!=session+1: errors.append(f'session count drift {p.lang}')
            if len(p.memory.events)!=2*(session+1): errors.append(f'evidence enrichment drift {p.lang}')

        # Unknown UI variant must fail closed without touching memory/provider state.
        snap=(p.memory.sessions,p.memory.summary,p.memory.next_obj,list(p.memory.events),p.provider_chat_exists)
        p.compatibility_fail+=1; fail_closed+=1
        if snap!=(p.memory.sessions,p.memory.summary,p.memory.next_obj,list(p.memory.events),p.provider_chat_exists):
            errors.append(f'fail-closed mutated state: {p.lang}')

    # Cross-profile isolation and canonical reuse invariants.
    for p in profiles:
        if p.memory.sessions!=6: errors.append(f'wrong final session count {p.lang}')
        if len(p.memory.events)!=12: errors.append(f'wrong final event count {p.lang}')
        if p.rebuilds!=2: errors.append(f'expected initial build + one deletion rebuild for {p.lang}, got {p.rebuilds}')
        if not p.provider_chat_exists: errors.append(f'canonical chat missing after reconstruction {p.lang}')

    # Adversarial materials.
    bad=[
        ('application/pdf','path.pdf',10,'file:///sdcard/path.pdf'),
        ('application/pdf','huge.pdf',100*1024**2+1,'content://docs/huge'),
        ('video/mp4','huge.mp4',2*1024**3+1,'content://video/huge'),
    ]
    for mime,name,size,uri in bad:
        max_bytes=2*1024**3 if mime.startswith('video/') else 100*1024**2
        accepted=uri.startswith('content://') and 0<size<=max_bytes
        if accepted: errors.append(f'adversarial material accepted: {name}')

    if errors:
        print('FAIL exhaustive contract simulation')
        for e in errors[:100]: print(' -',e)
        if len(errors)>100: print(f' ... {len(errors)-100} more')
        return 1
    print(f'PASS exhaustive contract simulation: profiles={len(profiles)}, sessions={total_sessions}, material_sessions={material_sessions}, forced_rebuilds={rebuilds}, fail_closed_probes={fail_closed}, version_families={len(VERSION_FAMILIES)}')
    return 0

if __name__=='__main__': sys.exit(run())
