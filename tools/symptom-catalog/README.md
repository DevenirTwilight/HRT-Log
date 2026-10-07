# symptom-sources.json generator

`build_catalog.py <download dir> app/src/main/resources/symptom-sources.json` rebuilds the bundled catalog of official symptom sources
(see `docs/wellbeing-research.md` §3–4 and `docs/wellbeing-design.md` §4). Every quote is checked verbatim
(whitespace-insensitive) against a downloaded copy of its source; the script stops on the first quote it cannot find.

The downloaded originals are **not** in the repository. To re-run, download the pages listed in the research document into the
same layout the script expects (`dl-drugs/pages/n_<CIS>.txt` text of each BDPM notice, `ansm.txt`, `tw2022.txt`,
`dl-cn/a1/…`, `dl-cn/a7/…`, `dl-has/has.txt`, `dl-region/{tw,fr,cdr}/…`) and run it with `python3 -I`.
Group names are neutral translations written for the app; they are not source text.
