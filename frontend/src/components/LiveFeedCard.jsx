import { useEffect, useState } from 'react'
import { Card } from './Card'
import { CameraIcon } from './Icons'
import { formatInteger } from '../utils/format'

/**
 * The ESP32-CAM live feed.
 *
 * The stream is an MJPEG multipart response, which an `img` element renders natively -
 * no player, no decoding library, no canvas (build plan Step 6.2). The URL comes from
 * /api/config rather than the bundle, because it is the board's DHCP address.
 *
 * The `reloadKey` remount is the important part. When the ESP32 browns out (Section 20.1)
 * or drops off the Wi-Fi, the browser reports the img as errored and never retries on its
 * own, so the panel would stay dead until someone reloaded the page. Retrying on a timer
 * means the feed comes back by itself once the board does.
 */
export function LiveFeedCard({
  streamUrl,
  personCount,
  sensorOnline,
  retryIntervalMs = 15000,
  connectTimeoutMs = 8000,
}) {
  const [failed, setFailed] = useState(false)
  const [streaming, setStreaming] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    setFailed(false)
    setStreaming(false)
  }, [streamUrl])

  useEffect(() => {
    if (!failed) {
      return undefined
    }
    const timer = setTimeout(() => {
      setFailed(false)
      setStreaming(false)
      setReloadKey((key) => key + 1)
    }, retryIntervalMs)
    return () => clearTimeout(timer)
  }, [failed, retryIntervalMs])

  /**
   * Treat a stream that never delivers a frame as failed.
   *
   * A request to an ESP32 that has browned out or moved to another IP hangs on the TCP
   * connect instead of erroring, so `onError` may not fire for a minute or more. Without
   * this the panel would show a LIVE badge over a black rectangle - which is exactly the
   * kind of confidently wrong display the DEGRADED state elsewhere exists to avoid.
   */
  useEffect(() => {
    if (!streamUrl || failed || streaming) {
      return undefined
    }
    const timer = setTimeout(() => setFailed(true), connectTimeoutMs)
    return () => clearTimeout(timer)
  }, [streamUrl, failed, streaming, reloadKey, connectTimeoutMs])

  const hasStream = Boolean(streamUrl) && !failed

  return (
    <Card
      title="Live Camera Feed"
      subtitle={streamUrl ? `ESP32-CAM MJPEG stream · ${streamUrl}` : 'ESP32-CAM MJPEG stream'}
      tools={<CameraIcon size={15} style={{ color: 'var(--text-muted)' }} />}
      className="card--feed"
    >
      <div className="feed">
        {hasStream ? (
          <img
            key={reloadKey}
            className="feed__img"
            src={streamUrl}
            alt="Live view of the classroom from the ESP32-CAM"
            onLoad={() => setStreaming(true)}
            onError={() => setFailed(true)}
          />
        ) : (
          <p className="feed__placeholder">
            {streamUrl ? (
              <>
                Camera stream unreachable. Retrying every {Math.round(retryIntervalMs / 1000)} s.
                <br />
                <code>{streamUrl}</code>
                <br />
                Check that the board is powered from the 5 V / 2 A supply — a USB port
                brown-outs the OV2640 during Wi-Fi transmission.
              </>
            ) : (
              <>
                No camera stream configured.
                <br />
                Set <code>smartroom.dashboard.camera-stream-url</code> to the address the
                CameraWebServer sketch prints on the serial monitor, e.g.{' '}
                <code>http://192.168.1.50:81/stream</code>.
              </>
            )}
          </p>
        )}

        <div className="feed__badges">
          {hasStream && streaming ? (
            <span className="feed__badge feed__badge--live">
              <span className="feed__pulse" />
              LIVE
            </span>
          ) : (
            <span className="feed__badge feed__badge--offline">
              {hasStream ? 'CONNECTING' : 'OFFLINE'}
            </span>
          )}

          {personCount !== null && personCount !== undefined && (
            <span className="feed__badge feed__badge--count">
              {formatInteger(personCount)} {personCount === 1 ? 'Person' : 'People'}
            </span>
          )}
        </div>
      </div>

      {!sensorOnline && (
        <p className="card__subtitle" style={{ marginTop: 8 }}>
          Sensor telemetry has stopped arriving — readings below are the last known values.
        </p>
      )}
    </Card>
  )
}
