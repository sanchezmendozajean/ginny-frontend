Deja aqui los logotipos por defecto de cada sistema, como PNG, nombrados exactamente igual
que la carpeta del sistema (ver SystemId.folderName en el codigo):

  nes.png
  snes.png
  gb.png
  gbc.png
  gba.png
  ps.png
  genesis.png
  mastersystem.png
  neogeo.png
  fbneo.png
  mame.png
  gc.png
  ps2.png
  psp.png
  dreamcast.png
  switch.png
  pico8.png

Estas imagenes se empaquetan en la APK y se copian a
"<Carpeta de Usuario>/Assets/<sistema>/system.png" la primera vez que el usuario
selecciona la carpeta de Usuario (o al detectar que falta ese archivo).

Si un PNG no esta presente aqui para un sistema dado, la app genera automaticamente un
logo de reemplazo simple (mosaico de color + sigla) en tiempo de ejecucion, para no dejar
nunca un sistema sin logo. Reemplaza esta carpeta con arte real cuando lo tengas listo y
recompila — no reproduzcas logos ni tipografias propietarias de Nintendo/Sega/Sony/etc.
