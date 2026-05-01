# Digital Wallet Frontend

React + JavaScript + Tailwind CSS frontend for the Digital Wallet and Rewards project.

## Run

1. Start the backend services so the API gateway is available on `http://localhost:8090`.
2. Copy `.env.example` to `.env` if you want to override the API base URL.
3. Install dependencies:
   `npm install`
4. Start the frontend:
   `npm run dev`
5. Open the app at `http://localhost:5173`

## Production build

`npm run build`

## Notes

- The app is wired to the existing backend endpoints through the API gateway.
- Admin and user experiences are role-aware based on the JWT returned by login.
- Features without backend support yet are shown as roadmap callouts instead of fake actions.
