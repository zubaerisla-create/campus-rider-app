import { useEffect, useState } from 'react'
import { Receipt } from 'lucide-react'
import RideList from '../../components/RideList'
import { Button, EmptyState, ErrorBanner, Spinner } from '../../components/ui'
import { errorMessage } from '../../services/api'
import { rideService } from '../../services/rideService'

export default function PassengerHistory() {
  const [rides, setRides] = useState(null)
  const [error, setError] = useState('')
  useEffect(() => {
    rideService.history().then(setRides).catch((e) => setError(errorMessage(e)))
  }, [])
  return (
    <div className="h-full overflow-y-auto">
      <div className="mx-auto max-w-2xl px-5 py-8">
        <h1 className="text-2xl font-bold tracking-tight">Your rides</h1>
        <ErrorBanner className="mt-4">{error}</ErrorBanner>
        {!rides && !error && <Spinner />}
        {rides && rides.length === 0 && (
          <EmptyState icon={Receipt} title="No rides yet" action={<Button to="/ride">Book a ride</Button>}>
            Your completed and cancelled trips will show up here.
          </EmptyState>
        )}
        {rides && rides.length > 0 && <RideList rides={rides} />}
      </div>
    </div>
  )
}
