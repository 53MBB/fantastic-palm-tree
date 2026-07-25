# MAGGuilds 4.0.26

Czysty, napisany od nowa plugin gildii dla Paper/Spigot 1.18.2-1.21.x.

## Najwazniejsze moduly

- tworzenie, usuwanie, zaproszenia, dolaczanie i zarzadzanie gildia,
- osobne centrum/serce, baza oraz dom gildii,
- role `Rekrut`, `Czlonek`, `Zaufany`, `Mistrz` i indywidualne uprawnienia,
- GUI czlonkow: LPM = indywidualne uprawnienia, PPM = nadanie roli,
- ochrona terenu i uprawnienia wysokosciowe dla wody/lawy,
- ranking graczy i gildii, kara -25/-25 za zabicie czlonka swojej gildii,
- antylogout z actionbarem `ᴀɴᴛʏʟᴏɢᴏᴜᴛ <czas>`,
- regeneracja z kosztem zaleznym od liczby blokow oraz BossBarem,
- wspolne, trudne osiagniecia gildii odbierane przez lidera lub zastepce,
- magazyn gildii,
- wojny i sojusze,
- PlaceholderAPI,
- wszystkie dane w YAML.

## Formatowanie 4.0.26

Smallcaps obejmuje tylko staly tekst. Placeholdery `%amount%`, `%material%`, `%time%`, `%blocks%`, `%percent%`, `%remaining%`, `%restored%` i `%tag%` sa podstawiane po formatowaniu i pozostaja zwykla czcionka.

## Build

```bash
mvn clean package
```

Gotowy plik: `target/MAGGuilds-4.0.26.jar`.
