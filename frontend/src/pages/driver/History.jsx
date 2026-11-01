import { useEffect, useState } from 'react'
import { Receipt } from 'lucide-react'
import RideList from '../../components/RideList'
import { Button, EmptyState, ErrorBanner, Spinner } from '../../components/ui'
import { errorMessage } from '../../services/api'
import { driverService } from '../../services/driverService'
import { taka } from '../../utils/format'

export default function DriverHistory() {
  const [rides, setRides] = useState(null)
  const [error, setError] = useState('')
  useEffect(() => {
    driverService.history().then(setRides).catch((e) => setError(errorMessage(e)))
  }, [])
  const earned = (rides || []).filter((r) => r.status === 'COMPLETED').reduce((s, r) => s + r.fare, 0)
  return (
    <div className="h-full overflow-y-auto">
      <div className="mx-auto max-w-2xl px-5 py-8">
        <div className="flex items-end justify-between">
          <h1 className="text-2xl font-bold tracking-tight">Trips</h1>
          {rides?.length > 0 && <p className="text-sm text-muted">Earned <span className="font-semibold text-ink">{taka(earned)}</span></p>}
        </div>
        <ErrorBanner className="mt-4">{error}</ErrorBanner>
        {!rides && !error && <Spinner />}
        {rides && rides.length === 0 && (
          <EmptyState icon={Receipt} title="No trips yet" action={<Button to="/drive">Go to dashboard</Button>}>
            Rides you accept will show up here.
          </EmptyState>
        )}
        {rides && rides.length > 0 && <RideList rides={rides} showPassenger />}
      </div>
    </div>
  )
}
