/**
 * What to tell the user about a failed request: the server's own explanation when it sent one
 * (a ProblemDetail's `detail`), else the error's message. Angular's default message for an HTTP
 * error ("Http failure response for ...") says nothing a person can act on.
 */
export function errorMessage(error: any): string {
  return error?.error?.detail ?? error?.message ?? 'Request failed';
}
