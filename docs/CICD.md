# CI/CD Guide

This project uses GitHub Actions for CI/CD.

## What Runs

CI runs on pushes and pull requests:

- Backend: `mvn -B clean verify`
- Frontend: `npm ci` and `npm run build`
- Docker: `docker compose build`
- Artifacts: backend test reports, JaCoCo reports, and frontend `dist`

CD runs on pushes to `main` or `master`, or manually from GitHub Actions:

- Builds every backend service Docker image from the root `Dockerfile`
- Builds the frontend Docker image from `frontend/Dockerfile`
- Pushes backend and frontend images to GitHub Container Registry
- Builds and uploads the frontend `dist` folder as a deploy artifact too

## How To Open And See It

1. Push this project to GitHub.
2. Open your repository in the browser.
3. Click the `Actions` tab.
4. Open the `CI` workflow to see test/build results.
5. Open the `CD` workflow to see Docker image publishing and frontend artifact builds.

## Where Docker Images Go

Backend images are pushed to GitHub Container Registry:

```text
ghcr.io/<your-github-username-or-org>/digitalwallet-eureka-server
ghcr.io/<your-github-username-or-org>/digitalwallet-config-server
ghcr.io/<your-github-username-or-org>/digitalwallet-api-gateway
ghcr.io/<your-github-username-or-org>/digitalwallet-auth-service
ghcr.io/<your-github-username-or-org>/digitalwallet-user-service
ghcr.io/<your-github-username-or-org>/digitalwallet-wallet-service
ghcr.io/<your-github-username-or-org>/digitalwallet-transaction-service
ghcr.io/<your-github-username-or-org>/digitalwallet-rewards-service
ghcr.io/<your-github-username-or-org>/digitalwallet-notification-service
ghcr.io/<your-github-username-or-org>/digitalwallet-admin-service
ghcr.io/<your-github-username-or-org>/digitalwallet-frontend
```

Each image gets two tags:

- `latest`
- the short commit SHA, for example `a1b2c3d`

## Manual CD Run

1. Go to GitHub repository.
2. Click `Actions`.
3. Click `CD`.
4. Click `Run workflow`.
5. Choose `push_images=true`.
6. Click the green `Run workflow` button.

## Local Commands

Use these before pushing if you want to check locally:

```powershell
mvn -B clean verify
cd frontend
npm ci
npm run build
cd ..
docker compose build
docker build -t digital-wallet-frontend:local ./frontend
```

## Important Notes

- GitHub Actions must be enabled in the repository.
- GitHub Container Registry publishing uses the built-in `GITHUB_TOKEN`, so no extra Docker password is needed.
- The current CD workflow publishes build artifacts and Docker images. It does not automatically deploy to a cloud server yet because no server details were provided.
- When you have a hosting target, add its secrets in GitHub repository settings and extend `cd.yml` with the deploy command.
