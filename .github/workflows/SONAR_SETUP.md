# SonarQube PR Hook Setup (GitHub)

This repository is configured with a PR workflow at:

- `.github/workflows/sonarqube-pr.yml`

It runs on every Pull Request update and is intended to run on a self-hosted runner in your private network.

## 1) Create required GitHub secrets

In your GitHub repository:

`Settings -> Secrets and variables -> Actions -> New repository secret`

Create these secrets:

- `SONAR_HOST_URL` (example: `http://10.8.72.9:9000`)
- `SONAR_TOKEN` (project/user analysis token from SonarQube)
- `SONAR_PROJECT_KEY` (exact project key in SonarQube)

## 2) Add a self-hosted runner in the same network as SonarQube

In GitHub:

`Settings -> Actions -> Runners -> New self-hosted runner`

Choose your OS and run the generated commands on a machine/VM that can access `SONAR_HOST_URL`.

Add the runner label `sonar-local`.

## 3) Verify network access from runner machine

From the runner host, verify SonarQube is reachable:

```bash
curl http://10.8.72.9:9000/api/system/status
```

Expected: JSON response (for example with status `UP`).

## 4) Push workflow and open a PR

Commit and push the workflow file.

Open a Pull Request (or update an existing one). The workflow `SonarQube PR Check` should start automatically.

## 5) Make the check mandatory before merge

In GitHub:

`Settings -> Branches -> Branch protection rule`

Enable:

- Require a pull request before merging
- Require status checks to pass before merging

Select the SonarQube workflow check.

## 6) SonarQube quality gate behavior

The workflow sets:

- `-Dsonar.qualitygate.wait=true`

This means the job fails if the quality gate fails.

## Notes

- If you use GitHub-hosted runners, private IPs like `10.x.x.x` are typically unreachable.
- If the runner cannot reach SonarQube, use a self-hosted runner inside the network or expose SonarQube securely via tunnel/reverse proxy.