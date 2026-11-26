// Dot-matrix clock for the Lock In page: the same 5x7 glyphs the app draws.
var G={0:'01110 10001 10011 10101 11001 10001 01110',1:'00100 01100 00100 00100 00100 00100 01110',2:'01110 10001 00001 00010 00100 01000 11111',3:'11111 00010 00100 00010 00001 10001 01110',4:'00010 00110 01010 10010 11111 00010 00010',5:'11111 10000 11110 00001 00001 10001 01110',6:'00110 01000 10000 11110 10001 10001 01110',7:'11111 00001 00010 00100 01000 01000 01000',8:'01110 10001 10001 01110 10001 10001 01110',9:'01110 10001 10001 01111 00001 00010 01100',':':'00000 00100 00100 00000 00100 00100 00000'};

function paint(){
  var c=document.getElementById('clock');if(!c)return;
  var w=Math.round(c.getBoundingClientRect().width);if(!w)return;
  var text='01:42:15',cell=w/(text.length*6-1),h=cell*7,dpr=window.devicePixelRatio||1;
  c.style.height=h+'px';c.width=Math.round(w*dpr);c.height=Math.round(h*dpr);
  var g=c.getContext('2d');g.setTransform(dpr,0,0,dpr,0,0);g.clearRect(0,0,w,h);
  var r=cell*0.3;
  for(var i=0;i<text.length;i++){
    var rows=(G[text[i]]||G[':']).split(' ');
    for(var y=0;y<7;y++)for(var x=0;x<5;x++){
      g.beginPath();g.fillStyle=rows[y].charAt(x)==='1'?'#D71921':'#1A0505';
      g.arc((i*6+x)*cell+r,y*cell+r,r,0,Math.PI*2);g.fill();
    }
  }
}
paint();
window.addEventListener('resize',paint);
var yr=document.getElementById('yr');if(yr)yr.textContent=new Date().getFullYear();
