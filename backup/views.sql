CREATE OR REPLACE VIEW popular_projects
AS SELECT
  p.name,
  count(c.change_id) as changes
FROM
  projects p,
  changes c
WHERE
  c.dest_project_key = p.gae_key
GROUP BY
  p.name
ORDER BY
  changes DESC;

CREATE OR REPLACE VIEW popular_files
AS SELECT
  j.name as project,
  p.filename,
  count(p.patchset_key) as changes
FROM
  patches p,
  projects j,
  patch_sets q,
  changes c
WHERE
  j.gae_key = c.dest_project_key
  AND c.gae_key = q.change_key
  AND q.gae_key = p.patchset_key
GROUP BY
  j.name,
  p.filename
ORDER BY
  changes DESC;

CREATE OR REPLACE VIEW change_totals
AS SELECT
  (select count(*) from changes where closed='Y' and merged='N') as abandoned,
  (select count(*) from changes where closed='Y' and merged='Y') as merged,
  (select count(*) from changes where closed='N') as active,
  (select count(*) from changes) as total;
