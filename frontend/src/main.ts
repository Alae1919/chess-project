// src/main.ts
import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';

// iOS Safari ignores user-scalable=no: this is what stops a pinch from zooming the page
document.addEventListener('gesturestart', (e) => e.preventDefault());

bootstrapApplication(AppComponent, appConfig)
  .catch((err) => console.error(err));
