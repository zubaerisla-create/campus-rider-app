import { Compass } from 'lucide-react'
import { Button, EmptyState } from '../components/ui'

export default function NotFound() {
  return (
    <div className="mx-auto max-w-lg py-16">
      <EmptyState icon={Compass} title="Page not found" action={<Button to="/">Back to home</Button>}>
        This page does not exist on campus.
      </EmptyState>
    </div>
  )
}
