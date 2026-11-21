const express = require('express');
const path = require('path');

const app = express();
const PORT = 3000;
const JAVA_API = 'http://localhost:8080';

// Capture raw body buffer for forwarding to the Java backend
app.use(express.raw({ type: '*/*' }));

// Forward all /api/* requests to Java HttpServer on port 8080
app.all('/api/*', async (req, res) => {
  const targetUrl = `${JAVA_API}${req.originalUrl}`;
  try {
    const fetchOptions = {
      method: req.method,
      headers: {
        'Content-Type': req.headers['content-type'] || 'application/json'
      }
    };

    if (req.method !== 'GET' && req.method !== 'HEAD' && req.body && req.body.length > 0) {
      fetchOptions.body = req.body;
    }

    const javaRes = await fetch(targetUrl, fetchOptions);
    const data = await javaRes.text();

    res.status(javaRes.status);
    res.setHeader('Content-Type', javaRes.headers.get('content-type') || 'application/json');
    res.send(data);
  } catch (err) {
    // When the Java server is offline or unreachable
    res.status(503).json({
      error: 'Java server offline',
      message: 'Cannot connect to Java backend on port 8080. Please start the Java server: javac *.java && java Server'
    });
  }
});

// Serve static frontend assets from public/
app.use(express.static(path.join(__dirname, 'public')));

// Fallback to index.html for root path
app.get('/', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

app.listen(PORT, () => {
  console.log('==================================================');
  console.log(` Frontend Server running at: http://localhost:${PORT}`);
  console.log(` Patient View:  http://localhost:${PORT}/`);
  console.log(` Admin Portal:  http://localhost:${PORT}/admin.html`);
  console.log(` Proxying API -> ${JAVA_API}`);
  console.log('==================================================');
});
