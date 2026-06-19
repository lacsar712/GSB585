export function createDebouncedFunction<T extends (...args: any[]) => void>(fn: T, delay = 400) {
  let timer: number | undefined

  const wrapped = (...args: Parameters<T>) => {
    if (timer !== undefined) {
      window.clearTimeout(timer)
    }
    timer = window.setTimeout(() => {
      fn(...args)
    }, delay)
  }

  wrapped.cancel = () => {
    if (timer !== undefined) {
      window.clearTimeout(timer)
      timer = undefined
    }
  }

  return wrapped
}
