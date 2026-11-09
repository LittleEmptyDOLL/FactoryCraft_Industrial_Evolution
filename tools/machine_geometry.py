"""Original unified machine geometry, expressed in actual footprint blocks, not extracted art.
Static surfaces are split by the client baker; item/moving meshes use normalized vanilla models.
Dimensions are parsed from MachineProfile.java to keep a single authoritative footprint table.
"""
import json,math,random,re

def generate(root,write,png):
 source=(root.parents[2]/'src/main/java/factorycraft/multiblock/MachineProfile.java').read_text()
 dimensions={name:(int(w),int(h),int(d)) for name,w,h,d in re.findall(r'\("([a-z0-9_]+)",\s*(\d+),\s*(\d+),\s*(\d+)\)',source)}
 palette={'shell':(72,87,101),'steel':(145,160,167),'dark':(23,32,41),'teal':(46,136,146),'orange':(196,107,47),'red':(161,62,51),'yellow':(218,174,51),'glass':(35,72,85),'hazard':(219,174,46),'inlet':(44,132,215),'outlet':(226,135,52),'outlet3':(226,135,52),'bidi':(55,176,157),'indicator_off':(36,60,63),'indicator_on':(72,230,163),'flame':(249,125,36),'belt':(156,120,43),'piston':(121,144,153)}
 for name,color in palette.items():
  frames=4 if name in ['flame','belt','piston'] else 2 if name=='indicator_on' else 1
  rng=random.Random('unified-'+name);canvas=[]
  for frame in range(frames):
   for y in range(64):
    row=[]
    for x in range(64):
     grain=rng.randrange(-5,6)+(y%4-2);c=tuple(max(0,min(255,v+grain)) for v in color)
     if name in ['steel','shell','teal','orange','red']:
      if y in [0,1,62,63] or x in [0,1,62,63]:c=tuple(int(v*.55) for v in color)
      if ((x-6)**2+(y-6)**2<7) or ((x-57)**2+(y-57)**2<7):c=(192,201,203)
     if name=='dark' and y%9<3:c=(11,18,24)
     if name=='glass':c=(31+x//5,68+x//7,79+x//6)
     if name=='hazard':c=(219,174,46) if (x+y)//10%2==0 else (25,32,38)
     if name in ['inlet','outlet','outlet3','bidi']:
      yy=63-y if name in ['outlet','outlet3'] else y
      if 27<=x<=36 and 10<=yy<=48 or 35<=yy<=48 and abs(x-31.5)<=yy-31:c=(221,236,235)
      if name=='bidi' and 15<=y<=28 and abs(x-31.5)<=31-y:c=(221,236,235)
     if name=='outlet3' and 5<=x<14 and 5<=y<20:
      if ['111','001','111','001','111'][(y-5)//3][(x-5)//3]=='1':c=(240,247,244)
     if name=='indicator_on':c=(50,200+frame*40,136+frame*16)
     if name=='flame':c=(255,min(255,80+(x*11+y*7+frame*38)%153),28+(y+frame*11)%40)
     if name=='belt':
      yy=(y+frame*12)%16;c=(52,60,64) if yy<4 else (91,99,99)
      if x<5 or x>58:c=(191,147,48)
     if name=='piston' and (y+frame*4)%16<5:c=(204,220,224)
     row.append((*c,255))
    canvas.append(row)
  png('block/machine/'+name,canvas)
  if frames>1:write(f'assets/factorycraft/textures/block/machine/{name}.png.mcmeta',{'animation':{'frametime':2 if name!='indicator_on' else 6,'interpolate':name!='belt'}})
 textures={name:'factorycraft:block/machine/'+name for name in palette};textures['particle']=textures['shell']
 specs=[];moving={}
 for name,(w,h,d) in dimensions.items():
  boxes=[];parts=[]
  def box(a,b,mat='shell',target=None):
   (boxes if target is None else target).append({'from':[round(v,5) for v in a],'to':[round(v,5) for v in b],'material':mat})
  def column(cx,cz,r,y0,y1,mat='shell'):
   # Three nested cross sections give an octagonal industrial silhouette with no copied mesh.
   box((cx-r*.65,y0,cz-r),(cx+r*.65,y1,cz+r),mat)
   box((cx-r,y0,cz-r*.65),(cx-r*.65,y1,cz+r*.65),mat)
   box((cx+r*.65,y0,cz-r*.65),(cx+r,y1,cz+r*.65),mat)
  def port(side,mat,front_center=None):
   x=w//2+.5 if front_center is None else front_center;z=d//2+.5
   if side in ['front','rear']:
    zz=(0,.16) if side=='front' else (d-.16,d)
    for a,b in [((x-.29,.2,zz[0]),(x-.2,.8,zz[1])),((x+.2,.2,zz[0]),(x+.29,.8,zz[1])),((x-.2,.2,zz[0]),(x+.2,.29,zz[1])),((x-.2,.71,zz[0]),(x+.2,.8,zz[1]))]:box(a,b,'steel')
    box((x-.195,.3,zz[0]),(x+.195,.7,zz[1]),mat)
   else:
    xx=(0,.16) if side=='left' else (w-.16,w)
    for a,b in [((xx[0],.2,z-.29),(xx[1],.8,z-.2)),((xx[0],.2,z+.2),(xx[1],.8,z+.29)),((xx[0],.2,z-.2),(xx[1],.29,z+.2)),((xx[0],.71,z-.2),(xx[1],.8,z+.2))]:box(a,b,'steel')
    box((xx[0],.3,z-.195),(xx[1],.7,z+.195),mat)
  if name in ['constant_combinator','arithmetic_combinator','decider_combinator','circuit_reader','circuit_controller']:
   trim={'constant_combinator':'yellow','arithmetic_combinator':'teal','decider_combinator':'orange','circuit_reader':'inlet','circuit_controller':'red'}[name]
   box((.05,0,.05),(w-.05,.15,d-.05),'dark');box((.12,.15,.12),(w-.12,.44,d-.12),trim)
   box((.22,.44,.22),(w-.22,.48,d-.22),'glass')
   for z in [.12,d-.12]:
    box((.15,.44,z-.06),(.30,.5,z+.06),'red');box((.70,.44,z-.06),(.85,.5,z+.06),'teal')
   box((.40,.441,.16),(.60,.49,.36),'indicator')
  elif name in ['splitter','fast_splitter','express_splitter']:
   trim={'splitter':'yellow','fast_splitter':'red','express_splitter':'inlet'}[name]
   box((0,0,0),(2,.10,1),'dark')
   for track in range(2):
    box((track+.08,.10,0),(track+.92,.18,1),'belt')
    box((track,.10,0),(track+.07,.1875,1),trim);box((track+.93,.10,0),(track+1,.1875,1),trim)
   box((.87,.11,.36),(1.13,.1875,.64),'hazard')
   if name!='splitter':
    for track in range(2):
     for side in [-1,1]:
      box((track+.5+side*.10-.035,.181,.16),(track+.5+side*.10+.035,.1875,.30),trim)
     box((track+.465,.181,.11),(track+.535,.1875,.21),trim)
  else:
   box((.05,0,.05),(w-.05,.24,d-.05),'dark')
   box((.08,.24,.08),(w-.08,.34,d-.08),'steel')
   box((.12,.12,.022),(.58,.22,.052),'indicator' if name!='fluid_tank' else 'bidi')
   if name=='electric_drill':
    box((.3,.34,.3),(2.7,.55,2.7),'teal')
    for x in [.14,2.64]:
     for z in [.14,2.64]:box((x,.34,z),(x+.22,1.84,z+.22),'steel')
    box((.12,1.68,.12),(2.88,1.94,.40),'teal');box((.12,1.68,2.6),(2.88,1.94,2.88),'teal')
    box((.12,1.68,.4),(.4,1.94,2.6),'teal');box((2.6,1.68,.4),(2.88,1.94,2.6),'teal')
    box((1.18,1.3,1.18),(1.82,1.92,1.82),'orange')
    for a,b in [((1.34,.35,1.34),(1.66,1.65,1.66)),((.66,.60,1.38),(2.34,.76,1.62)),((1.38,.60,.66),(1.62,.76,2.34))]:box(a,b,'steel',parts)
   elif name in ['electric_furnace','industrial_furnace']:
    box((.25,.34,.25),(2.75,2.5,2.75),'orange' if name=='electric_furnace' else 'red')
    box((.42,2.5,.42),(2.58,2.79,2.58),'dark')
    box((.35,.6,.13),(2.65,1.95,.3),'steel');box((.57,.78,.10),(2.43,1.73,.18),'dark')
    box((.4,.35,.08),(2.6,.55,.25),'hazard')
    for x in [.12,2.7]:box((x,.5,.55),(x+.18,2.35,2.5),'steel')
    column(2.2,2.2,.28,2.6,2.98,'steel')
    box((.63,.84,.085),(2.37,1.66,.095),'flame',parts)
   elif name in ['assembling_machine','assembling_machine_2']:
    box((.27,.34,.27),(2.73,.75,2.73),'teal');box((.45,.75,.45),(2.55,.85,2.55),'steel')
    for x in [.16,2.64]:
     for z in [.16,2.64]:box((x,.34,z),(x+.2,2.88,z+.2),'steel')
    box((.15,2.55,.15),(2.85,2.96,2.85),'teal');box((.55,.85,.55),(2.45,1.02,2.45),'hazard')
    box((.85,1.65,.85),(2.15,1.93,2.15),'steel',parts);box((1.34,1.93,1.34),(1.66,2.58,1.66),'orange',parts)
    if name=='assembling_machine_2':
     box((.35,1.05,2.65),(.75,2.4,2.95),'inlet');box((2.25,1.05,2.65),(2.65,2.4,2.95),'inlet')
   elif name=='beacon':
    box((.4,.34,.4),(2.6,1.1,2.6),'shell')
    box((1.25,1.1,1.25),(1.75,2.85,1.75),'steel')
    box((.3,1.8,1.35),(2.7,2.05,1.65),'teal',parts)
    box((1.35,1.8,.3),(1.65,2.05,2.7),'teal',parts)
    box((1.1,2.65,1.1),(1.9,2.98,1.9),'indicator')
   elif name=='lab':
    box((.3,.34,.3),(2.7,.78,2.7),'teal');box((.55,.78,.55),(2.45,.88,2.45),'steel')
    for x in [.25,2.55]:
     for z in [.25,2.55]:box((x,.7,z),(x+.2,2.6,z+.2),'steel')
    box((.35,2.45,.35),(2.65,2.8,2.65),'teal')
    for x in [.8,1.8]:column(x,1.5,.25,.9,2.2,'glass')
    box((.65,1.3,1.12),(2.35,1.42,1.88),'orange',parts)
   elif name=='coal_generator':
    box((.3,.34,.35),(2.7,2.35,2.65),'shell');box((.48,.55,.18),(2.52,2.1,.36),'dark')
    for x in [.5,2.5]:column(x,2.42,.23,2.2,2.98,'steel')
    box((.4,2.35,.45),(2.6,2.62,2.15),'yellow')
    box((.8,1.1,.13),(2.2,1.3,.24),'steel',parts);box((1.4,.5,.13),(1.6,1.9,.24),'steel',parts)
   elif name=='pumpjack':
    box((.35,.34,.4),(2.65,.85,2.6),'teal')
    for x in [.5,2.28]:box((x,.7,1.25),(x+.22,2.85,1.65),'steel')
    box((.4,2.45,1.12),(2.6,2.7,1.78),'orange')
    box((1.3,2.75,.28),(1.7,3.06,2.72),'teal',parts);box((1.08,2.5,.26),(1.92,3.45,.66),'orange',parts)
    box((.9,.85,2.05),(2.1,1.7,2.65),'shell')
   elif name=='oil_refinery':
    column(1.2,2.3,.8,.34,3.7,'shell');column(3.7,3.4,.7,.34,3.2,'teal');column(3.65,1.35,.65,.34,2.55,'steel')
    for x in [.65,1.65]:box((x,.8,1.4),(x+.1,3.45,3.2),'steel')
    box((.28,2.45,.8),(4.72,2.62,1.0),'hazard');box((.3,2.35,1.0),(.45,2.6,4.55),'steel')
    box((1.1,1.65,.9),(4.15,1.9,1.1),'orange');box((2.8,.34,.35),(4.55,1.35,.85),'dark')
    box((3.2,.7,.25),(4.2,.84,.35),'steel',parts);box((3.63,.27,.25),(3.77,1.27,.35),'steel',parts)
   elif name=='chemical_plant':
    column(1.05,1.55,.67,.34,2.2,'teal');column(2.1,1.75,.6,.34,2.55,'steel')
    box((.3,2.35,.6),(2.7,2.58,.82),'orange');box((.25,.35,.6),(.42,2.8,.82),'steel');box((2.58,.35,.6),(2.75,2.8,.82),'steel')
    box((.5,2.7,.55),(2.5,2.9,.85),'hazard')
    box((1.38,1.3,1.35),(1.62,2.6,1.65),'steel',parts);box((.95,2.27,1.4),(2.05,2.43,1.6),'orange',parts)
   elif name=='boiler':
    column(1.5,1.05,.72,.34,1.75,'shell');box((.32,.4,.15),(2.68,.8,1.85),'orange')
    box((.25,.85,.08),(1.08,1.55,.24),'steel');box((.4,1,.055),(.92,1.4,.09),'dark')
    column(2.55,1.45,.21,1.35,1.98,'steel');box((.42,1.02,.035),(.90,1.38,.045),'flame',parts)
   elif name=='steam_engine':
    box((.38,.34,2.0),(2.62,1.7,4.55),'teal');box((.58,1.7,2.2),(2.42,1.9,4.25),'steel')
    box((1.2,.4,.45),(1.8,.65,4.6),'dark')
    box((.7,.92,.86),(2.3,1.08,1.08),'orange',parts);box((1.42,.2,.86),(1.58,1.8,1.08),'orange',parts)
    box((.9,.4,3.5),(2.1,.7,4.9),'shell')
   elif name=='fluid_tank':
    column(1.5,1.5,1.21,.34,2.68,'teal');column(1.5,1.5,1.07,2.68,2.88,'steel')
    for y in [.55,1.55,2.45]:
     box((.23,y,.65),(2.77,y+.09,2.35),'steel');box((.65,y,.23),(2.35,y+.09,2.77),'steel')
    box((1.32,.65,.18),(1.68,2.25,.28),'glass');box((1.2,2.88,1.2),(1.8,3,1.8),'bidi')
   elif name=='electric_pump':
    box((.18,.34,.3),(.82,.77,1.7),'teal');box((.26,.77,.4),(.74,.96,.98),'shell')
    box((.32,.44,.18),(.68,.67,.62),'piston')
  specs.append({'profile':name,'boxes':boxes})
  def vanilla(mesh,scale,offset):
   elements=[]
   for b in mesh:
    a=[round(b['from'][i]*scale[i]+offset[i],5) for i in range(3)];z=[round(b['to'][i]*scale[i]+offset[i],5) for i in range(3)]
    elements.append({'from':a,'to':z,'faces':{f:{'texture':'#'+('indicator_off' if b['material']=='indicator' else b['material']),'uv':[0,0,16,16]} for f in ['down','up','north','south','west','east']}})
   return {'parent':'minecraft:block/block','textures':textures,'elements':elements}
  # Item ports use the same descriptor as server capabilities and the chunk mesh baker.
  item_ports=[]
  layouts=json.loads((root/'factorycraft/machine_layouts.json').read_text())['machines']
  for p in layouts.get(name,{}).get('ports',[]):
   x,y,z=p['at'];side=p['face'];mat={'INPUT':'inlet','OUTPUT':'outlet','BOTH':'bidi'}[p['mode']]
   a=[x+.27,y+.27,z+.27];b=[x+.73,y+.73,z+.73]
   axis=0 if side in ['east','west'] else 2
   a[axis]=[x,y,z][axis]+(.84 if side in ['east','south'] else .001)
   b[axis]=[x,y,z][axis]+(.999 if side in ['east','south'] else .16)
   item_ports.append({'from':a,'to':b,'material':mat})
  unit=max(w,h,d);scale=14/unit
  write(f'assets/factorycraft/models/item/{name}.json',vanilla(boxes+parts+item_ports,[scale]*3,[(16-w*scale)/2,.5,(16-d*scale)/2]))
  if parts:
   write(f'assets/factorycraft/models/block/machine/{name}_moving.json',vanilla(parts,[16/unit]*3,[0,0,0]));moving[name]=True
  write(f'assets/factorycraft/blockstates/{name}.json',{'variants':{'':{'model':'factorycraft:block/machine_shell'}}})
 for casing in ['machine_casing','refinery_casing']:write(f'assets/factorycraft/blockstates/{casing}.json',{'variants':{'':{'model':'factorycraft:block/machine_shell'}}})
 write('assets/factorycraft/models/block/machine_shell.json',{'parent':'minecraft:block/block','loader':'factorycraft:machine','textures':textures,'machines':specs})
 return moving
